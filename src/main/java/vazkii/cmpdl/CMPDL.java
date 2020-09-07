package vazkii.cmpdl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CMPDL {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern CURSE_FORGE_MOD_PACK_PATTERN = Pattern.compile("https://www\\.curseforge\\" +
            ".com/minecraft/modpacks/(?<pack>[0-9a-z-]+)/(?:(?:files)|(?:download))/(?<version>\\d+)(?:/file)?/?");
    private static final String META_URL = "https://addons-ecs.forgesvc.net/api/v2/addon/0/file/%s/download-url";
    private static final String MOD_URL = "https://addons-ecs.forgesvc.net/api/v2/addon/%s/file/%s";
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Ubuntu Chromium/53.0.2785.143 Chrome/53.0.2785.143 Safari/537.36";

    public static boolean downloading = false;
    public static List<Addon> missingMods = null;

    private CMPDL() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            if (args.length != 1) {
                System.out.println("USAGE:");
                System.out.println("cmpdl | open interface");
                System.out.println("cmpdl <url> | download the specified pack version");
                System.exit(1);
            }

            String url = args[0];
            try {
                downloadFromURL(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            Interface.openInterface();
        }
    }

    public static void downloadFromURL(String url) throws Exception {
        if (downloading) {
            return;
        }

        missingMods = new ArrayList<>();
        downloading = true;
        log("~ Starting magical modpack download sequence ~");
        log("Input URL: " + url);
        Interface.setStatus("Starting up");

        Matcher matcher = CURSE_FORGE_MOD_PACK_PATTERN.matcher(url);
        if (!matcher.matches()) {
            Interface.error();
            log("");
            log("Given URL was not a valid CurseForge Modpack File URL.");
            return;
        }

        String pack = matcher.group("pack");
        String version = matcher.group("version");

        log("Modpack Name: " + pack);
        log("Version: " + version);
        log("");

        Path tmpDir = setupTempDir();

        Path extractedDir = downloadModpackMetadata(pack, version, tmpDir);

        doSetup(extractedDir, extractedDir.getFileName().toString());
    }

    public static void setupFromLocalFile(Path file) throws Exception {
        if (downloading) {
            return;
        }

        missingMods = new ArrayList<>();
        downloading = true;

        String fileName = file.getFileName().toString();
        log("Modpack zip file is " + fileName);
        log("");

        Path tmpDir = setupTempDir();

        Path extractedDir = setupLocalModpackMetadata(file, tmpDir);

        doSetup(extractedDir, fileName);
    }

    private static void doSetup(Path extractedDir, String fileName) throws Exception {
        Manifest manifest = getManifest(extractedDir);

        Path outputDir = getOutputDir(fileName);
        log("Output Dir " + outputDir);

        Path minecraftDir = outputDir.resolve("minecraft");
        if (!Files.exists(minecraftDir)) {
            Files.createDirectories(minecraftDir);
        }

        downloadModpackFromManifest(minecraftDir, manifest);
        copyOverrides(manifest, extractedDir, minecraftDir);
        setupMultimcInfo(manifest, outputDir);

        Interface.finishDownload(false);

        endSetup(outputDir, manifest);
    }

    public static void endSetup(Path outputDir, Manifest manifest) throws IOException {
        log("And we're done!");
        log("Output Path: " + outputDir);
        log("");
        log("################################################################################################");
        log("IMPORTANT NOTE: If you want to import this instance to MultiMC, you must install Forge manually");
        log("The Forge version you need is " + manifest.getForgeVersion());
        log("A later version will probably also work just as fine, but this is the version shipped with the pack");
        log("This is also added to the instance notes");

        if (!missingMods.isEmpty()) {
            log("");
            log("WARNING: Some mods could not be downloaded. Either the specific versions were taken down from "
                    + "public download on CurseForge, or there were errors in the download.");
            log("The missing mods are the following:");
            for (Addon addon : missingMods) {
                log(" - " + addon);
            }
            log("");
            log("If these mods are crucial to the modpack functioning, try downloading the server version of the "
                    + "pack and pulling them from there.");
        }
        missingMods = null;

        log("################################################################################################");

        Interface.setStatus("Complete");

        Desktop.getDesktop().open(outputDir.toFile());
    }

    public static Path downloadModpackMetadata(String pack, String version, Path tmpDir) throws IOException {
        log("Setting up Metadata");
        Interface.setStatus("Setting up Metadata");

        String url = resolveModpackUrl(pack, version);

        String zipName = URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8.name());
        String extractedName = zipName.replaceAll("\\.zip", "");
        Path zipFile = tmpDir.resolve(zipName);
        Path extractedDir = tmpDir.resolve(extractedName);

        log("Downloading Modpack zip file to " + zipFile);
        Interface.setStatus("Downloading Modpack zip file");

        downloadFileFromURL(zipFile, new URL(url));

        log("Extracting Modpack zip file to " + extractedDir);
        Interface.setStatus("Extracting Modpack zip file");

        extractModpackMetadata(zipFile, extractedDir);

        return extractedDir;
    }

    public static Path setupLocalModpackMetadata(Path zipFile, Path tmpDir) throws IOException {
        log("Setting up Metadata");
        Interface.setStatus("Setting up Metadata");

        String zipName = zipFile.getFileName().toString();
        String extractedName = zipName.replaceAll("\\.zip", "");
        Path extractedDir = tmpDir.resolve(extractedName);

        log("Extracting Modpack zip file " + zipName);
        Interface.setStatus("Extracting Modpack zip file");

        extractModpackMetadata(zipFile, extractedDir);

        return extractedDir;
    }

    public static Path setupTempDir() throws IOException {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path tmpDir = userHome.resolve(".cmpdl").toAbsolutePath().normalize();
        Files.createDirectories(tmpDir);
        log("CMPDL Temp Dir is " + tmpDir);
        return tmpDir;
    }

    public static void extractModpackMetadata(Path zipFile, Path extractDir) throws IOException {
        Interface.setStatus("Unzipping Modpack Download");

        if (Files.exists(extractDir)) {
            log("Cleaning up extract dir");
            Files.walk(extractDir)
                    .sorted(Comparator.reverseOrder())
                    .forEachOrdered(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        log("Unzipping file");
        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
            List<Path> roots = new ArrayList<>();
            zipFs.getRootDirectories().forEach(roots::add);

            if (roots.size() != 1) {
                throw new RuntimeException("Too many roots in Zip Filesystem, expected 1");
            }

            Path root = roots.get(0);
            ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            Files.walk(root).filter(Files::isRegularFile).forEach(p -> es.submit(() -> {
                Path extracted = extractDir.resolve(root.relativize(p).toString());
                System.out.println(p + " -> " + extracted);
                try {
                    Files.createDirectories(extracted.getParent());
                    Files.copy(p, extracted, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));

            es.shutdown();

            try {
                es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        log("Done unzipping");
    }

    public static Manifest getManifest(Path tmpDir) throws IOException {
        Interface.setStatus("Parsing Manifest");
        Path manifestFile = tmpDir.resolve("manifest.json");
        if (!Files.exists(manifestFile)) {
            throw new IllegalArgumentException("This modpack has no manifest");
        }

        log("Parsing Manifest");
        return GSON.fromJson(Files.newBufferedReader(manifestFile), Manifest.class);
    }

    public static Path downloadModpackFromManifest(Path outputDir, Manifest manifest) throws IOException,
            URISyntaxException {
        int total = manifest.files.size();

        log("Downloading modpack from Manifest");
        log("Manifest contains " + total + " files to download");
        log("");

        int left = total;
        for (Manifest.FileData f : manifest.files) {
            left--;
            downloadFile(f, outputDir, left, total);
        }

        log("Mod downloads complete");

        return outputDir;
    }

    public static void copyOverrides(Manifest manifest, Path extractedDir, Path outDir) throws IOException {
        Interface.setStatus("Copying modpack overrides");
        log("Copying modpack overrides");
        Path overridesDir = extractedDir.resolve(manifest.overrides);

        Files.walk(overridesDir).filter(Files::isRegularFile).forEach(p -> {
            log("Override: " + p.getFileName());
            Path copied = outDir.resolve(overridesDir.relativize(p));
            try {
                Files.createDirectories(copied.getParent());
                Files.copy(p, copied, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        log("Done copying overrides");
    }

    public static void setupMultimcInfo(Manifest manifest, Path outputDir) throws IOException {
        log("Setting up MultiMC info");
        Interface.setStatus("Setting up MultiMC info");

        Path cfg = outputDir.resolve("instance.cfg");
        if (!Files.exists(cfg)) {
            Files.createFile(cfg);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(cfg)) {
            writer.write("InstanceType=OneSix\n"
                    + "IntendedVersion=" + manifest.minecraft.version + "\n"
                    + "LogPrePostOutput=true\n"
                    + "OverrideCommands=false\n"
                    + "OverrideConsole=false\n"
                    + "OverrideJavaArgs=false\n"
                    + "OverrideJavaLocation=false\n"
                    + "OverrideMemory=false\n"
                    + "OverrideWindow=false\n"
                    + "iconKey=default\n"
                    + "lastLaunchTime=0\n"
                    + "name=" + manifest.name + " " + manifest.version + "\n"
                    + "notes=Modpack by " + manifest.author + ". Generated by CMPDL. Using Forge " + manifest.getForgeVersion() + ".\n"
                    + "totalTimePlayed=0\n");
        }
    }

    public static Path getOutputDir(String fileName) throws IOException, URISyntaxException {
        Path jarFile = Paths.get(CMPDL.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path homePath = jarFile.getParent();

        String outname = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
        outname = outname.replaceAll("\\.zip", "");
        Path outDir = homePath.resolve(outname);

        log("Output Dir is " + outDir);

        if (!Files.exists(outDir)) {
            log("Directory doesn't exist, making it now");
            Files.createDirectories(outDir);
        }

        return outDir.toAbsolutePath().normalize();
    }

    public static void downloadFile(Manifest.FileData fileData, Path outputDir, int remaining, int total) throws IOException, URISyntaxException {
        log("Downloading file data " + fileData);
        Interface.setStatus("File: " + fileData + " (" + (total - remaining) + "/" + total + ")");
        Interface.setStatus2("Acquiring Info");

        Addon addon = resolveModUrl(fileData.projectID, fileData.fileID);

        String url = addon.downloadUrl;
        log("File download URL is " + url);

        String fileName = addon.fileName;
        Interface.setStatus2("Downloading " + fileName);

        String installDir_ = addon.guessInstallDir();
        log("Installing to " + installDir_);

        Path installDir = outputDir.resolve(installDir_);
        if (!Files.exists(installDir)) {
            Files.createDirectories(installDir);
        }
        Path file = installDir.resolve(fileName);

        try {
            if (Files.exists(file)) {
                log("This file already exists. No need to download it");
            } else {
                downloadFileFromURL(file, new URL(url));
                log("Downloaded!");
            }
            log(remaining + "/" + total + " remaining");
        } catch (Exception e) {
            log("Error: " + e.getClass().toString() + ": " + e.getLocalizedMessage());
            log("This mod will not be downloaded. If you need the file, you'll have to get it " +
                    "manually:");
            log(Objects.toString(addon));
            missingMods.add(addon);
            e.printStackTrace();
        }

        log("");
    }

    private static String resolveModpackUrl(String pack, String version) throws IOException {
        URL url = new URL(String.format(META_URL, version));
        List<String> lines = readLinesFromURL(url);
        if (lines.size() != 1) {
            throw new RuntimeException();
        }
        return lines.get(0);
    }

    private static Addon resolveModUrl(int project, int version) throws IOException {
        URL url = new URL(String.format(MOD_URL, project, version));
        List<String> lines = readLinesFromURL(url);
        if (lines.size() != 1) {
            throw new RuntimeException();
        }
        return GSON.fromJson(String.join("\n", lines), Addon.class);
    }

    public static ReadableByteChannel open(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        return Channels.newChannel(conn.getInputStream());
    }

    public static List<String> readLinesFromURL(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);

        List<String> lines = new ArrayList<>();
        String line;
        try (
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))
        ) {
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        return lines;
    }

    public static void downloadFileFromURL(Path f, URL url) throws IOException {
        try (
                ReadableByteChannel in = open(url);
                FileChannel out = FileChannel.open(f, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            out.transferFrom(in, 0, Long.MAX_VALUE);
        }
    }

    public static void log(String s) {
        Interface.addLogLine(s);
        System.out.println(s);
    }
}
