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
        public int resultCount;
    }

    public List<Data> data;
    public Pagination pagination;


    private Data getData() {
        return this.data.stream().findFirst().orElseThrow(RuntimeException::new);
    }

    public String getZipDownload() {
        if (pagination.resultCount > 1) {
            throw new RuntimeException("MORE THEN 1 MODPACK FOUND BY PROVIDED SLUG");
        }

        return getData().latestFiles.stream()
                .max(Comparator.comparing(Addon.Data::getId))
                .map(s -> s.downloadUrl)
                .stream()
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    public Integer getModpackId() {
        return Optional.of(getData().id).orElseThrow(RuntimeException::new);
    }
}
