package vazkii.cmpdl;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public final class CMPDL {

	public static final Gson GSON_INSTANCE = new Gson();

	public static final Pattern FILE_NAME_URL_PATTERN = Pattern.compile(".*?/([^/]*)$");

	public static boolean downloading = false;

	public static void main(String[] args) {
		if(args.length > 0) {
			String url = args[0];
			try {
				downloadFromURL(url);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		} else Interface.openInterface();
	}

	public static void downloadFromURL(String url) throws Exception {
		if(downloading)
			return;

		downloading = true;
		log("~ Starting magical modpack download sequence ~");
		Interface.setStatus("Starting up");

		String packUrl = url;
		if(packUrl.endsWith("/"))
			packUrl = packUrl.replaceAll(".$", "");

		String fileSuffix = "/files/latest";
		if(packUrl.contains("/files/")) {
			int splitIdx = packUrl.indexOf("/files/");
			fileSuffix = packUrl.substring(splitIdx);
			packUrl = packUrl.substring(0, splitIdx);
		}

		if(!fileSuffix.endsWith("/latest")) {
			fileSuffix += "/download";
		}

		String fileUrl = packUrl + fileSuffix;
		String finalUrl = getLocationHeader(fileUrl);
		log("URLs: " + fileUrl + " " + finalUrl);
		Matcher matcher = FILE_NAME_URL_PATTERN.matcher(finalUrl);
		if(matcher.matches()) {
			String filename = matcher.group(1);
			log("Modpack filename is " + filename);

			File unzippedDir = setupModpackMetadata(filename, finalUrl);
			Manifest manifest = getManifest(unzippedDir);
			File outputDir = getOutputDir(filename);

			File minecraftDir = new File(outputDir, "minecraft");
			if(!minecraftDir.exists())
				minecraftDir.mkdir();

			downloadModpackFromManifest(minecraftDir, manifest);
			copyOverrides(manifest, unzippedDir, minecraftDir);
			setupMultimcInfo(manifest, outputDir);

			downloading = false;

			log("And we're done!");
			log("Output Path: " + outputDir);
			log("");
			log("################################################################################################");
			log("IMPORTANT NOTE: If you want to import this instance to MultiMC, you must install Forge manually");
			log("The Forge version you need is " + manifest.getForgeVersion());
			log("A later version will probably also work just as fine, but this is the version shipped with the pack");
			log("This is also added to the instance notes");
			log("################################################################################################");
			Interface.setStatus("Complete");

			Desktop.getDesktop().open(outputDir);
		} else {
			downloading = false;
			Interface.setStatus("Errored");
		}
	}

	public static File setupModpackMetadata(String filename, String url) throws IOException, ZipException {
		Interface.setStatus("Setting up Metadata");

		File homeDir = getTempDir();

		File retDir = new File(homeDir, filename);
		log("Modpack temporary directory is " + retDir);
		if(!retDir.exists()) {
			log("Directory doesn't exist, making it now");
			retDir.mkdir();
		}

		String zipName = filename;
		if(!zipName.endsWith(".zip"))
			zipName = zipName + ".zip";		

		String retPath = retDir.getAbsolutePath();
		retDir.deleteOnExit();

		log("Downloading zip file " + zipName);
		Interface.setStatus("Downloading Modpack .zip");
		File f = new File(retDir, zipName);
		downloadFileFromURL(f, new URL(url));

		Interface.setStatus("Unzipping Modpack Download");
		log("Unzipping file");
		ZipFile zip = new ZipFile(f);
		zip.extractAll(retPath);

		log("Done unzipping");

		return retDir;
	}

	public static File getTempDir() {
		String home = System.getProperty("user.home");
		File homeDir = new File(home, "/.cmpdl_temp");

		log("CMPDL Temp Dir is " + homeDir);
		if(!homeDir.exists()) {
			log("Directory doesn't exist, making it now");
			homeDir.mkdir();
		}

		return homeDir;
	}

	public static Manifest getManifest(File dir) throws IOException {
		Interface.setStatus("Parsing Manifest");
		File f = new File(dir, "manifest.json");
		if(!f.exists())
			throw new IllegalArgumentException("This modpack has no manifest");

		log("Parsing Manifest");
		Manifest manifest = GSON_INSTANCE.fromJson(new FileReader(f), Manifest.class);

		return manifest;
	}

	public static File downloadModpackFromManifest(File outputDir, Manifest manifest) throws IOException, URISyntaxException {
		int total = manifest.files.size();

		log("Downloading modpack from Manifest");
		log("Manifest contains " + total + " files to download");

		File modsDir = new File(outputDir, "mods");
		if(!modsDir.exists())
			modsDir.mkdir();

		int left = total;
		for(Manifest.FileData f : manifest.files) {
			left--;
			downloadFile(f, modsDir, left, total); 
		}

		log("Mod downloads complete");

		return outputDir;
	}

	public static void copyOverrides(Manifest manifest, File tempDir, File outDir) throws IOException {
		Interface.setStatus("Copying modpack overrides");
		log("Copying modpack overrides");
		File overridesDir = new File(tempDir, manifest.overrides);

		Files.walk(overridesDir.toPath()).forEach(path -> {
			try {
				Files.copy(path, Paths.get(path.toString().replace(overridesDir.toString(), outDir.toString())));
			} catch(IOException e) { }
		});
		log("Done copying overrides");
	}

	public static void setupMultimcInfo(Manifest manifest, File outputDir) throws IOException {
		log("Setting up MultiMC info");
		Interface.setStatus("Setting up MultiMC info");

		File cfg = new File(outputDir, "instance.cfg");
		if(!cfg.exists())
			cfg.createNewFile();

		try(BufferedWriter writer = new BufferedWriter(new FileWriter(cfg))) {
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

	public static File getOutputDir(String filename) throws IOException, URISyntaxException {
		File jarFile = new File(CMPDL.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		String homePath = jarFile.getParentFile().getAbsolutePath();

		String outname = URLDecoder.decode(filename, "UTF-8");
		outname = outname.replaceAll(".zip", "");
		File outDir = new File(homePath, outname);

		log("Output Dir is " + outDir);

		if(!outDir.exists()) {
			log("Directory doesn't exist, making it now");
			outDir.mkdir();
		}

		return outDir;
	}

	public static void downloadFile(Manifest.FileData file, File modsDir, int remaining, int total) throws IOException, URISyntaxException {
		log("Downloading " + file);
		Interface.setStatus("Acquiring Info for file " + file + " (" + (total - remaining) + "/" + total + ")");

		String baseUrl = "http://minecraft.curseforge.com/projects/" + file.projectID;
		log("Project URL is " + baseUrl);

		String projectUrl = getLocationHeader(baseUrl);
		projectUrl = projectUrl.replaceAll("\\?cookieTest=1", "");
		String fileDlUrl = projectUrl + "/files/" + file.fileID + "/download";
		log("File download URL is " + fileDlUrl);

		String finalUrl = getLocationHeader(fileDlUrl);
		Matcher m = FILE_NAME_URL_PATTERN.matcher(finalUrl);
		if(!m.matches())
			throw new IllegalArgumentException("Mod file doesn't match filename pattern");

		String filename = m.group(1);
		filename = URLDecoder.decode(filename, "UTF-8");

		Interface.setStatus("Downloading " + filename + " (" + (total - remaining) + "/" + total + ")");

		if(filename.endsWith("cookieTest=1"))
			log("Missing file! Skipping it");
		else {
			log("Downloading " + filename);

			File f = new File(modsDir, filename);
			if(f.exists())
				log("This file already exists. No need to download it");
			else downloadFileFromURL(f, new URL(finalUrl));

			log("Downloaded! " + remaining + "/" + total + " remaining");
		}
	}

	public static String getLocationHeader(String location) throws IOException, URISyntaxException {
		URI uri = new URI(location);
		HttpURLConnection connection = null;
		String userAgent="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/53.0.2785.143 Chrome/53.0.2785.143 Safari/537.36";
		for(;;) {
			URL url = uri.toURL();
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setInstanceFollowRedirects(false);
			String redirectLocation = connection.getHeaderField("Location");
			if(redirectLocation == null) 
				break;

			redirectLocation = redirectLocation.replaceAll("\\[", "%5B");
			redirectLocation = redirectLocation.replaceAll("\\]", "%5D");
			
			if(redirectLocation.startsWith("/"))
				uri = new URI(uri.getScheme(), uri.getHost(), redirectLocation, uri.getFragment());
			else uri = new URI(redirectLocation);
		}

		return uri.toString();
	}

	public static void downloadFileFromURL(File f, URL url) throws IOException {
		if(!f.exists())
			f.createNewFile();

		try(InputStream instream = url.openStream(); FileOutputStream outStream = new FileOutputStream(f)) {
			byte[] buff = new byte[4096];

			int i;
			while((i = instream.read(buff)) > 0)
				outStream.write(buff, 0, i);
		}
	}

	public static void log(String s) {
		Interface.addLogLine(s);
		System.out.println(s); 
	}

	private CMPDL() {}
}
