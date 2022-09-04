package vazkii.cmpdl;

import okio.Okio;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CMPDL {

    private static final Pattern CURSE_FORGE_MOD_PACK_PATTERN = Pattern.compile("https://www\\.curseforge\\.com/minecraft/modpacks/(?<pack>[\\da-z-]+)(?:/(?:files|download)/(?<fileId>\\d+))?");
    private static final String META_URL = "https://api.curseforge.com/v1/mods/search?gameId=432&slug=%s";
    private static final String MOD_URL = "https://api.curseforge.com/v1/mods/%s/files/%s";
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/53.0.2785.143 Chrome/53.0.2785.143 Safari/537.36";

    private static final Object lock = new Object();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static final List<Addon.Data> missingMods = new ArrayList<>();
    public static boolean downloading = false;
    public static String apiToken;

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

        missingMods.clear();
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
        String fileId = matcher.group("fileId");

        log("Modpack Name: " + pack);
        log("");

        Path tmpDir = setupTempDir();

        Path extractedDir = downloadModpackMetadata(pack, tmpDir);

        doSetup(extractedDir, extractedDir.getFileName().toString());
    }

    public static void setupFromLocalFile(Path file) throws Exception {
        if (downloading) {
            return;
        }

        missingMods.clear();
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
        setupMultiMcInfo(manifest, outputDir);

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
            for (Addon.Data addon : missingMods) {
                log(" - " + addon);
            }
            log("");
            log("If these mods are crucial to the modpack functioning, try downloading the server version of the "
                    + "pack and pulling them from there.");
        }
        missingMods.clear();

        log("################################################################################################");

        Interface.setStatus("Complete");

        Desktop.getDesktop().open(outputDir.toFile());
    }

    public static Path downloadModpackMetadata(String pack, Path tmpDir) throws IOException {
        log("Setting up Metadata");
        Interface.setStatus("Setting up Metadata");

        String url = resolveModpackUrl(pack);

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
        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, null)) {
            List<Path> roots = new ArrayList<>();
            zipFs.getRootDirectories().forEach(roots::add);

            if (roots.size() != 1) {
                throw new RuntimeException("Too many roots in Zip Filesystem, expected 1");
            }

            Path root = roots.get(0);
            try (Stream<Path> stream = Files.walk(root)) {
                List<? extends Future<?>> futures = stream
                        .filter(Files::isRegularFile)
                        .map(p -> executorService.submit(() -> {
                            Path extracted = extractDir.resolve(root.relativize(p).toString());
                            System.out.println(p + " -> " + extracted);
                            try {
                                Files.createDirectories(extracted.getParent());
                                Files.copy(p, extracted, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }))
                        .collect(Collectors.toList());

                futures.forEach(f -> {
                    try {
                        f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
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
        return Manifest.ADAPTER.fromJson(Okio.buffer(Okio.source(manifestFile)));
    }

    public static void downloadModpackFromManifest(Path outputDir, Manifest manifest) {
        int total = manifest.files.size();

        Interface.setStatus("Downloading " + total + " files");

        log("Downloading modpack from Manifest");
        log("Manifest contains " + total + " files to download");

        AtomicInteger remaining = new AtomicInteger(total);
        List<? extends Future<?>> futures = manifest.files.stream()
                .map(f -> executorService.submit(() -> downloadFile(f, outputDir, remaining)))
                .collect(Collectors.toList());

        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                log("Error: " + e.getClass().toString() + ": " + e.getLocalizedMessage());
                e.printStackTrace();
            }
        });

        log("Mod downloads complete");
        log("");
    }

    public static void copyOverrides(Manifest manifest, Path extractedDir, Path outDir) throws IOException {
        Interface.setStatus("Handling modpack overrides");
        log("Handling modpack overrides");
        Interface.setStatus2("Collecting overrides");

        Path overridesDir = extractedDir.resolve(manifest.overrides);
        List<Path> overrides;
        try (Stream<Path> stream = Files.walk(overridesDir)) {
            overrides = stream
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }

        AtomicInteger remaining = new AtomicInteger(overrides.size());
        Interface.setStatus2("Copying " + remaining.get() + " overrides");
        log("Copying " + remaining.get() + " overrides");

        List<? extends Future<?>> futures = overrides.stream()
                .map(p -> executorService.submit(() -> {
                    Path copied = outDir.resolve(overridesDir.relativize(p));
                    try {
                        Files.createDirectories(copied.getParent());
                        Files.copy(p, copied, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }

                    int newRemaining = remaining.decrementAndGet();
                    if (newRemaining % 10 == 0) {
                        synchronized (lock) {
                            log("Remaining: " + newRemaining);
                        }
                    }
                }))
                .collect(Collectors.toList());

        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                log("Error: " + e.getClass().toString() + ": " + e.getLocalizedMessage());
                e.printStackTrace();
            }
        });

        log("Done copying overrides");
        log("");
    }

    public static void setupMultiMcInfo(Manifest manifest, Path outputDir) throws IOException {
        log("Setting up MultiMC info");
        Interface.setStatus("Setting up MultiMC info");

        Path cfg = outputDir.resolve("instance.cfg");
        try (BufferedWriter writer = Files.newBufferedWriter(cfg)) {
            writer.write("InstanceType=OneSix\n"
                    + "IntendedVersion=" + manifest.minecraft.version + "\n"
                    + "iconKey=default\n"
                    + "name=" + manifest.name + " " + manifest.version + "\n"
                    + "notes=Modpack by " + manifest.author + ". Generated by CMPDL. Using Forge " + manifest.getForgeVersion() + ".\n");
        }

        log("");
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

    public static void downloadFile(Manifest.FileData fileData, Path outputDir, AtomicInteger remaining) {
        Addon addon = null;
        try {
            addon = resolveModUrl(fileData.projectID, fileData.fileID);

            String url = addon.data.downloadUrl;
            String fileName = addon.data.fileName;
            String installDir_ = addon.guessInstallDir();

            Path installDir = outputDir.resolve(installDir_);
            try {
                Files.createDirectories(installDir);
            } catch (Exception ignored) {
            }

            Path file = installDir.resolve(fileName);
            if (!Files.exists(file)) {
                downloadFileFromURL(file, new URL(url));
            }

            int newRemaining = remaining.decrementAndGet();
            if (newRemaining % 10 == 0) {
                synchronized (lock) {
                    log("Remaining: " + newRemaining);
                }
            }
        } catch (Exception e) {
            synchronized (lock) {
                log("This mod will not be downloaded. If you need the file, you'll have to get it manually:");
                if (addon != null) {
                    log(addon.data.toString());
                    missingMods.add(addon.data);
                } else {
                    log(fileData.toString());
                }
                log("");
            }

            throw new RuntimeException(e);
        }
    }

    private static String resolveModpackUrl(String slug) throws IOException {
        URL url = new URL(String.format(META_URL, slug));
        List<String> lines = readLinesFromURL(url);
        if (lines.size() != 1) {
            throw new RuntimeException();
        }

        Optional<Pack> pack = Optional.ofNullable(Pack.ADAPTER.fromJson(String.join("\n", lines)));

        return pack.orElseThrow(RuntimeException::new).getZipDownload().orElseThrow(RuntimeException::new);
    }

    private static Addon resolveModUrl(int project, int version) throws IOException {
        URL url = new URL(String.format(MOD_URL, project, version));
        List<String> lines = readLinesFromURL(url);
        if (lines.size() != 1) {
            throw new RuntimeException();
        }

        return Addon.ADAPTER.fromJson(String.join("\n", lines));
    }

    public static ReadableByteChannel open(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        return Channels.newChannel(conn.getInputStream());
    }

    public static List<String> readLinesFromURL(URL url) throws IOException {
        if (apiToken.isEmpty()) {
            log("NO API TOKEN PROVIDED");
            throw new RuntimeException("NO API TOKEN SET");
        }


        URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("x-api-key", apiToken);

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
