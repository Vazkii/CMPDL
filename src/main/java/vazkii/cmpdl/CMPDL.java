package vazkii.cmpdl;

import com.google.gson.Gson;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CMPDL {

    public static final Gson GSON_INSTANCE = new Gson();
    public static final Pattern FILE_NAME_URL_PATTERN = Pattern.compile(".*?/([^/]*)$");
    public static String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/53.0.2785.143 Chrome/53.0.2785.143 Safari/537.36";

    public static boolean downloading = false;
    public static List<String> missingMods = null;

    public static void main(String[] args) {
        if (args.length > 0) {
            String url = args[0];
            String version = "latest";
            if (args.length > 1)
                version = args[1];
            try {
                downloadFromURL(url, version);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            Interface.openInterface();
        }
    }

    public static void downloadFromURL(String url, String version) throws Exception {
        if (downloading) {
            return;
        }

        if (url.contains("feed-the-beast.com") && (version == null || version.isEmpty() || version.equals("latest"))) {
            log("WARNING: For modpacks hosted in the FTB site, you need to provide a version, \"latest\" will not work!");
            log("To find the version number to insert in the Curse File ID field, click the latest file on the sidebar on the right of the modpack's page.");
            log("The number you need to input is the number at the end of the URL.");
            log("For example, if you wanted to download https://www.feed-the-beast.com/projects/ftb-presents-skyfactory-3/files/2390075");
            log("Then you would use 2390075 as the Curse File ID. Do not change the Modpack URL. Change that and click Download again to continue.");
            Interface.setStatus("Awaiting Further Input");
            return;
        }

        if (url.contains("curseforge.com") && (version == null || version.isEmpty() || version.equals("latest"))) {
            log("WARNING: packversion \"latest\" is currently unsupported as curseforge have changed their url format.");
            log("To find the version number to insert in the Curse File ID field, check the url of the file on the modpack's \"files\" page.");
            log("The number you need to input is the number at the end of the URL.");
            log("For example, if you wanted to download https://www.curseforge.com/minecraft/modpacks/crucial/files/2732998");
            log("Then you would use 2732998 as the Curse File ID. Do not change the Modpack URL. Change that and click Download again to continue.");
            Interface.setStatus("Awaiting Further Input");
            return;
        }

        missingMods = new ArrayList<>();
        downloading = true;
        log("~ Starting magical modpack download sequence ~");
        log("Input URL: " + url);
        log("Input Version: " + version);
        Interface.setStatus("Starting up");

        String packUrl = url;
        if (packUrl.endsWith("/")) {
            packUrl = packUrl.replaceAll(".$", "");
        }

        String packVersion = version;

        String fileUrl;
        fileUrl = packUrl + "/download/" + packVersion + "/file";

        String finalUrl = getLocationHeader(fileUrl);
        log("File URL: " + fileUrl);
        log("Final URL: " + finalUrl);
        log("");

        Matcher matcher = FILE_NAME_URL_PATTERN.matcher(finalUrl);
        if (matcher.matches()) {
            String filename = matcher.group(1);
            log("Modpack filename is " + filename);

            Path unzippedDir = downloadModpackMetadata(filename, finalUrl);
            Manifest manifest = getManifest(unzippedDir);
            Path outputDir = getOutputDir(filename);

            Path minecraftDir = outputDir.resolve("minecraft");
            if (!Files.exists(minecraftDir)) {
                Files.createDirectories(minecraftDir);
            }

            downloadModpackFromManifest(minecraftDir, manifest);
            copyOverrides(manifest, unzippedDir, minecraftDir);
            setupMultimcInfo(manifest, outputDir);

            Interface.finishDownload(false);

            endSetup(outputDir, manifest);
        } else {
            Interface.error();
        }
    }

    public static void setupFromLocalFile(Path file) throws Exception {
        missingMods = new ArrayList<>();
        downloading = true;

        String filename = file.getFileName().toString();
        log("Modpack filename is " + filename);

        Path unzippedDir = setupLocalModpackMetadata(file);
        Manifest manifest = getManifest(unzippedDir);
        Path outputDir = getOutputDir(filename);

        Path minecraftDir = outputDir.resolve("minecraft");
        if (!Files.exists(minecraftDir)) {
            Files.createDirectories(minecraftDir);
        }

        downloadModpackFromManifest(minecraftDir, manifest);
        copyOverrides(manifest, unzippedDir, minecraftDir);
        setupMultimcInfo(manifest, outputDir);

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
            for (String mod : missingMods) {
                log(" - " + mod);
            }
            log("");
            log("If these mods are crucial to the modpack functioning, try downloading the server version of the pack "
                    + "and pulling them from there.");
        }
        missingMods = null;

        log("################################################################################################");

        Interface.setStatus("Complete");

        Desktop.getDesktop().open(outputDir.toFile());
    }

    public static Path downloadModpackMetadata(String filename, String url) throws IOException {
        Interface.setStatus("Setting up Metadata");

        String zipName = filename;
        if (!zipName.endsWith(".zip")) {
            zipName = zipName + ".zip";
        }

        Path retDir = setupTemporaryDirectory(filename);

        log("Downloading zip file " + zipName);
        Interface.setStatus("Downloading Modpack .zip");
        Path f = retDir.resolve(zipName);
        downloadFileFromURL(f, new URL(url));

        extractModpackMetadata(f, retDir);

        return retDir;
    }

    public static Path setupLocalModpackMetadata(Path file) throws IOException {
        Interface.setStatus("Setting up Metadata");

        Path retDir = setupTemporaryDirectory(file.getFileName().toString());

        extractModpackMetadata(file, retDir);

        return retDir;
    }

    public static Path setupTemporaryDirectory(String filename) throws IOException {
        Path tempDir = Files.createTempDirectory("cmdpl");
        log("CMPDL Temp Dir is " + tempDir);
        tempDir.toFile().deleteOnExit();

        return tempDir;
    }

    public static void extractModpackMetadata(Path f, Path retDir) throws IOException {
        Interface.setStatus("Unzipping Modpack Download");
        log("Unzipping file");

        try (FileSystem zipFs = FileSystems.newFileSystem(f, null)) {
            List<Path> roots = new ArrayList<>();
            zipFs.getRootDirectories().forEach(roots::add);

            if (roots.size() != 1) {
                throw new RuntimeException("Too many roots in Zip Filesystem, expected 1");
            }

            Path root = roots.get(0);
            ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                es.submit(() -> {
                    Path extracted = retDir.resolve(root.relativize(p).toString());
                    try {
                        Files.createDirectories(extracted.getParent());
                        Files.copy(p, extracted, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            });

            es.shutdown();

            try {
                es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        log("Done unzipping");
    }

    public static Manifest getManifest(Path dir) throws IOException {
        Interface.setStatus("Parsing Manifest");
        Path f = dir.resolve("manifest.json");
        if (!Files.exists(f)) {
            throw new IllegalArgumentException("This modpack has no manifest");
        }

        log("Parsing Manifest");
        Manifest manifest = GSON_INSTANCE.fromJson(Files.newBufferedReader(f), Manifest.class);

        return manifest;
    }

    public static Path downloadModpackFromManifest(Path outputDir, Manifest manifest) throws IOException, URISyntaxException {
        int total = manifest.files.size();

        log("Downloading modpack from Manifest");
        log("Manifest contains " + total + " files to download");
        log("");

        Path modsDir = outputDir.resolve("mods");
        if (!Files.exists(modsDir)) {
            Files.createDirectories(modsDir);
        }

        int left = total;
        for (Manifest.FileData f : manifest.files) {
            left--;
            downloadFile(f, modsDir, left, total);
        }

        log("Mod downloads complete");

        return outputDir;
    }

    public static void copyOverrides(Manifest manifest, Path tempDir, Path outDir) throws IOException {
        Interface.setStatus("Copying modpack overrides");
        log("Copying modpack overrides");
        Path overridesDir = tempDir.resolve(manifest.overrides);

        Files.walk(overridesDir).forEach(path -> {
            try {
                log("Override: " + path.getFileName());
                Files.copy(path, Paths.get(path.toString().replace(overridesDir.toString(), outDir.toString())));
            } catch (IOException e) {
                if (!(e instanceof FileAlreadyExistsException))
                    log("Error copying " + path.getFileName() + ": " + e.getMessage() + ", " + e.getClass());
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

    public static Path getOutputDir(String filename) throws IOException, URISyntaxException {
        Path jarFile = Path.of(CMPDL.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path homePath = jarFile.getParent();

        String outname = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        outname = outname.replaceAll("\\.zip", "");
        Path outDir = homePath.resolve(outname);

        log("Output Dir is " + outDir);

        if (!Files.exists(outDir)) {
            log("Directory doesn't exist, making it now");
            Files.createDirectories(outDir);
        }

        return outDir;
    }

    public static void downloadFile(Manifest.FileData file, Path modsDir, int remaining, int total) throws IOException, URISyntaxException {
        log("Downloading " + file);
        Interface.setStatus("File: " + file + " (" + (total - remaining) + "/" + total + ")");
        Interface.setStatus2("Acquiring Info");

        String baseUrl = "http://minecraft.curseforge.com/projects/" + file.projectID;
        log("Project URL is " + baseUrl);

        String projectUrl = getLocationHeader(baseUrl);
        projectUrl = projectUrl.replaceAll("\\?cookieTest=1", "");
        String fileDlUrl = projectUrl + "/download/" + file.fileID + "/file";
        log("File download URL is " + fileDlUrl);

        String finalUrl = getLocationHeader(fileDlUrl);
        Matcher m = FILE_NAME_URL_PATTERN.matcher(finalUrl);
        if (!m.matches()) {
            throw new IllegalArgumentException("Mod file doesn't match filename pattern");
        }

        String filename = m.group(1);
        filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);

        Interface.setStatus2("Downloading");

        if (filename.endsWith("cookieTest=1")) {
            log("Missing file! Skipping it");
            missingMods.add(finalUrl);
        } else {
            log("Downloading " + filename);

            Path f = modsDir.resolve(filename);
            try {
                if (filename.equals("download"))
                    throw new FileNotFoundException("Invalid filename");

                if (Files.exists(f)) {
                    log("This file already exists. No need to download it");
                } else {
                    downloadFileFromURL(f, new URL(finalUrl));
                }
                log("Downloaded! " + remaining + "/" + total + " remaining");
            } catch (IOException e) {
                Interface.addLogLine("Error: " + e.getClass().toString() + ": " + e.getLocalizedMessage());
                Interface.addLogLine("This mod will not be downloaded. If you need the file, you'll have to get it manually:");
                Interface.addLogLine(finalUrl);
                CMPDL.missingMods.add(finalUrl);
            }
        }

        log("");
    }

    public static String getLocationHeader(String location) throws IOException, URISyntaxException {
        URI uri = new URI(location);
        HttpURLConnection connection;

        for (; ; ) {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setInstanceFollowRedirects(false);
            String redirectLocation = connection.getHeaderField("Location");
            if (redirectLocation == null) {
                break;
            }

            if (redirectLocation.startsWith("/")) {
                uri = new URI(uri.getScheme(), uri.getHost(), redirectLocation, uri.getFragment());
            } else {
                try {
                    uri = new URI(redirectLocation);
                } catch (URISyntaxException e) {
                    url = new URL(redirectLocation);
                    uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
                }
            }
        }

        return uri.toString();
    }

    public static ReadableByteChannel open(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", userAgent);
        return Channels.newChannel(conn.getInputStream());
    }

    public static void downloadFileFromURL(Path f, URL url) throws IOException {
        try (
                ReadableByteChannel in = open(url);
                FileChannel out = FileChannel.open(f, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ) {
            out.transferFrom(in, 0, Long.MAX_VALUE);
        }
    }

    public static void log(String s) {
        Interface.addLogLine(s);
        System.out.println(s);
    }

    private CMPDL() {
    }
}
