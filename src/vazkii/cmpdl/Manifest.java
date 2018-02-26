package vazkii.cmpdl;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.FileReader;
import java.lang.NumberFormatException;
import vazkii.cmpdl.Manifest.MinecraftData.Modloader;

public class Manifest {

	public MinecraftData minecraft;
	public String manifestType;
	public String manifestVersion;
	public String name;
	public String version;
	public String author;
	public int projectID;
	public List<FileData> files;
	public String overrides;

	public String getForgeVersion() {
		for(Modloader loader : minecraft.modLoaders) {
			if(loader.id.startsWith("forge"))
				return loader.id.substring("forge-".length());
		}

		return "N/A";
	}

	public static class MinecraftData {

		public String version;
		public List<Modloader> modLoaders;

		public static class Modloader {

			public String id;
			public boolean primary;

		}

	}

	public static class FileData {

		public int projectID;
		public int fileID;
		public boolean required;

		@Override
		public String toString() {
			return projectID + "/" + fileID;
		}

	}

	public static Manifest createFromMcModInfo(File f, String url) throws IOException
	{
		JsonParser parser = new JsonParser();
		FileReader reader = new FileReader(f);
		JsonArray root = parser.parse(reader).getAsJsonArray();

		Manifest manifest = new Manifest();
		JsonObject modInfo = root.get(0).getAsJsonObject();


		manifest.minecraft = new MinecraftData();
		manifest.minecraft.version = modInfo.get("mcversion").getAsString();
		manifest.minecraft.modLoaders = new ArrayList<MinecraftData.Modloader>();
		manifest.manifestType = "mcmod.info";
		manifest.manifestVersion = "N/A";
		manifest.name = modInfo.get("name").getAsString();
		manifest.version = modInfo.get("version").getAsString();

		JsonArray authors = modInfo.get("authorList").getAsJsonArray();
		if (authors.size() == 0)
			manifest.author = "";
		else if (authors.size() == 1){
			manifest.author = authors.get(0).getAsString();
		} else {
			manifest.author = String.format("%s and others", authors.get(0).getAsString());
		}

		try{
			String projectId = url.substring(url.lastIndexOf("/")+1);
			manifest.projectID = Integer.parseInt(projectId);
		}catch(NumberFormatException exception){
			System.err.printf("WARNING! Can't parse project id from URL %s, assuming 0\n", url);
			manifest.projectID = 0;
		}

		manifest.files = new ArrayList<FileData>();
		manifest.overrides="";
		return manifest;
	}

}
