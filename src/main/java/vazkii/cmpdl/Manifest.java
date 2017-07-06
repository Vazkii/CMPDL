package vazkii.cmpdl;

import java.util.List;

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

}
