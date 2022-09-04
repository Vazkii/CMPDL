package vazkii.cmpdl;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Pack {

    public static final JsonAdapter<Pack> ADAPTER = new Moshi.Builder().build().adapter(Pack.class);


    public static class Data {
        public int id;
        public String name;
        public String slug;
        public List<Addon.Data> latestFiles;


        @Override
        public String toString() {
            return "Pack{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", slug='" + slug + '\'' +
                    '}';
        }
    }

    public static class Pagination {
        public int index;
        public int pageSize;
        public int resultCount;
        public int totalCount;
    }

    public List<Data> data;
    public Pagination pagination;

    public Optional<String> getZipDownload() {
        return this.data.get(0).latestFiles.stream()
                .max(Comparator.comparing(Addon.Data::getId))
                .map(s -> s.downloadUrl)
                .stream().findFirst();
    }

    public Optional<Integer> getModpackId() {
        return this.data.get(0).latestFiles.stream()
                .max(Comparator.comparing(Addon.Data::getId))
                .map(s -> s.id)
                .stream().findFirst();
    }
}
