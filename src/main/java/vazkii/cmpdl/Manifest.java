package vazkii.cmpdl;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import vazkii.cmpdl.Manifest.MinecraftData.Modloader;

import java.util.List;

public class Manifest {

    public static final JsonAdapter<Manifest> ADAPTER = new Moshi.Builder().build().adapter(Manifest.class);

    public MinecraftData minecraft;
    public String manifestType;
    public String manifestVersion;
    public String name;
    public String version;
    public String author;
    public List<FileData> files;
    public String overrides;

    public String getForgeVersion() {
        for (Modloader loader : minecraft.modLoaders) {
            if (loader.id.startsWith("forge")) {
                return loader.id.substring("forge-".length());
            }
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
            return "project=" + projectID + " / file=" + fileID;
        }
    }
}
