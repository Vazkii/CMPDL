package vazkii.cmpdl;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Addon {

    public static final JsonAdapter<Addon> ADAPTER = new Moshi.Builder().build().adapter(Addon.class);

    public int id;
    public String displayName;
    public String fileName;
    public List<Module> modules;
    public String downloadUrl;

    public String guessInstallDir() {
        Set<String> folderNames = modules.stream()
                .map(m -> m.foldername)
                .collect(Collectors.toSet());

        if (fileName.endsWith(".zip")
                && folderNames.contains("assets")
                && folderNames.contains("pack.mcmeta")) {
            return "resourcepacks";
        }
        return "mods";
    }

    @Override
    public String toString() {
        return "Addon{" +
                "id=" + id +
                ", displayName='" + displayName + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }

    public static class Module {

        public String foldername;
        public long fingerprint;

    }
}
