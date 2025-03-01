package Tools.UI;

import arc.Core;
import arc.files.Fi;
import arc.func.Floatc;
import arc.scene.ui.layout.Table;
import arc.util.ArcRuntimeException;
import arc.util.Http;
import arc.util.Strings;
import arc.util.io.Streams;
import arc.util.serialization.Jval;

import static mindustry.Vars.*;
import static mindustry.Vars.mods;

public class UpdaterTable {
    private float modImportProgress;

    Table updater = new Table();

    public UpdaterTable(Table parents){

    }

    private void githubImportJavaMod(String repo){
        Http.get("https://api.github.com/repos/Ovulam5480/OvulamTools/releases/latest", res -> {
            var json = Jval.read(res.getResultAsString());
            var assets = json.get("assets").asArray();

            var dexedAsset = assets.find(j -> j.getString("name").startsWith("dexed") && j.getString("name").endsWith(".jar"));
            var asset = dexedAsset == null ? assets.find(j -> j.getString("name").endsWith(".jar")) : dexedAsset;

            if(asset != null){
                var url = asset.getString("browser_download_url");

                Http.get(url, this::handleMod, this::importFail);
            }else{
                throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
            }
        }, this::importFail);
    }

    private void importFail(Throwable t){
        Core.app.post(() -> modError(t));
    }

    void modError(Throwable error){
        ui.loadfrag.hide();

        if(error instanceof NoSuchMethodError || Strings.getCauses(error).contains(t -> t.getMessage() != null && (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            ui.showErrorMessage("@feature.unsupported");
        }else if(error instanceof Http.HttpStatusException st){
            ui.showErrorMessage(Core.bundle.format("connectfail", Strings.capitalize(st.status.toString().toLowerCase())));
        }else{
            ui.showException(error);
        }
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
                    ui.loadfrag.hide();
                }catch(Throwable e){
                    ui.showException(e);
                }
            });
        }catch(Throwable e){
            modError(e);
        }
    }
}
