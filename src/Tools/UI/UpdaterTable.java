package Tools.UI;

import arc.Core;
import arc.files.Fi;
import arc.func.Floatc;
import arc.scene.ui.layout.Table;
import arc.util.Http;
import arc.util.Strings;
import arc.util.io.Streams;
import arc.util.serialization.Jval;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;

import static mindustry.Vars.*;
import static mindustry.Vars.mods;

public class UpdaterTable{
    private float modImportProgress;
    private final Bar bar = new Bar(() -> modImportProgress == 0 ? "" : "正在下载中", () -> Pal.accent, () -> modImportProgress);
    private final String proxy = "https://ghfast.top/";
    public static boolean hasBuild;
    public static Jval.JsonArray assets;

    public UpdaterTable(Table parents){
        parents.table(t -> {
            t.button(Icon.download, this::githubImportJavaMod).left().row();
            t.add(bar).height(32).growX();
        }).left();
        hasBuild = true;
    }

    private void githubImportJavaMod(){
        var dexedAsset = assets.find(j -> j.getString("name").startsWith("dexed") && j.getString("name").endsWith(".jar"));
        var asset = dexedAsset == null ? assets.find(j -> j.getString("name").endsWith(".jar")) : dexedAsset;

        var url = asset.getString("browser_download_url");

        modImportProgress = 0f;

        Http.get(proxy + url, this::handleMod, e -> Http.get(url, this::handleMod, this::fail));
    }

    private void fail(Throwable t){
        ui.announce("[red]下载失败", 3f);
    }

    private void handleMod(Http.HttpResponse result){
        try{
            Fi file = tmpDirectory.child("Ovulam5480/OvulamTools".replace("/", "") + ".zip");
            long len = result.getContentLength();
            Floatc cons = len <= 0 ? f -> {} : p -> modImportProgress = p;

            try(var stream = file.write(false)){
                Streams.copyProgress(result.getResultAsStream(), stream, len, 4096, cons);
            }

            var mod = mods.importMod(file);
            mod.setRepo("Ovulam5480/OvulamTools");
            file.delete();
            Core.app.post(() -> {
                try{
                    ui.announce("更新成功, 重启游戏有效", 3f);
                }catch(Throwable e){
                    fail(e);
                }
            });
        }catch(Throwable e){
            fail(e);
        }
    }
}
