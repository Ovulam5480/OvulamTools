package Tools.UI;

import Tools.Tools;
import Tools.UI.ShortcutsSchematics.ShortcutsSchematicsTable;
import Tools.copy.PublicStaticVoids;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.DesktopInput;
import mindustry.input.MobileInput;
import mindustry.logic.LAssembler;
import mindustry.logic.LExecutor;
import mindustry.logic.LUnitControl;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockStatus;

import java.util.Arrays;

import static Tools.type.SchematicPlacer.canReplace;
import static arc.Core.camera;
import static mindustry.Vars.*;
import static mindustry.world.meta.BlockFlag.*;
import static mindustry.world.meta.BlockFlag.drill;

public class ButtonsTable {
    private final FunctionButton[] functionButtons = new FunctionButton[]{
            new FunctionButton("让玩家单位转圈圈", Icon.rotate, () -> {
                Unit unit = player.unit();
                if (unit == null) return;
                unit.rotation = unit.type.rotateSpeed * unit.speedMultiplier * Time.time * 1.2f;
            }),

            new FunctionButton("让玩家控制的单位无视单位方向, 总是以单位的1.2倍最大速度的移动", Icon.right, () -> {
            }) {
                final Vec2 movement = new Vec2(), pos = new Vec2();

                @Override
                public void init() {
                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked) {
                            Draw.z(Layer.fogOfWar + 1f);
                            if (state.rules.limitMapArea)
                                Tmp.r1.set(state.rules.limitX * tilesize, state.rules.limitY * tilesize, state.rules.limitWidth * tilesize, state.rules.limitHeight * tilesize).grow(finalWorldBounds * 2);
                            else world.getQuadBounds(Tmp.r1);

                            Drawf.dashRect(Pal.remove, Tmp.r1);
                            Draw.reset();
                        }
                    });

                    update = () -> {
                        Unit unit = player.unit();
                        if (unit == null) return;

                        pos.set(unit.x, unit.y);
                        float speed = unit.speed() * 1.2f;

                        if (mobile) {
                            movement.set(((MobileInput)control.input).movement).nor().scl(speed);
                        } else if (!Core.scene.hasField()) {
                            movement.set(((DesktopInput)control.input).movement).nor().scl(speed);
                        }
                        pos.add(movement);

                        if (unit.canPass(World.toTile(pos.x), World.toTile(pos.y))) {
                            unit.vel.set(movement.x, movement.y);

                            if (!movement.isZero()) {
                                float a = movement.angle() - unit.rotation;
                                unit.rotation(movement.angle());

                                for (WeaponMount mount : unit.mounts) {
                                    mount.rotation -= a;
                                }
                            }
                        }
                    };
                }
            },

            new FunctionButton("PVE显示敌方进攻路线, V8适用情况暂时未知", Icon.distribution, () -> {}) {
                final Color[] colors = {Pal.ammo, Pal.suppress, Liquids.water.color};
                final Seq<Tile> spawners = new Seq<>();
                final ObjectMap<Tile, Integer> buildingSpawners = new ObjectMap<>();
                final ObjectMap<Building, Tile> coreSpawners = new ObjectMap<>();

                private final BlockFlag[] randomTargets = {storage, generator, launchPad, factory, repair, battery, reactor, drill};

                final IntSeq[] tileSeqs = new IntSeq[3];
                final Building[] random = new Building[randomTargets.length];
                Tile tmp;

                @Override
                public void init() {
                    check = () -> {
                        Tools.copyPathfinder.setShouldUpdate(true);
                        addSpawners();
                    };
                    checkOff = () -> Tools.copyPathfinder.setShouldUpdate(false);

                    for (int i = 0; i < 3; i++) {
                        tileSeqs[i] = new IntSeq();
                    }

                    Events.on(EventType.WorldLoadEvent.class, e -> addSpawners());

                    Events.on(EventType.UnitCreateEvent.class, e -> {
                        if (!state.rules.pvp && e.unit.team != player.team() && e.unit.isGrounded() && e.spawner != null) {
                            Tile spawnTile = spawnTile(e.spawner);

                            if (spawnTile != null && !spawnTile.solid()) {
                                //buildingSpawners.put(spawnTile, e.unit.pathType());
                                //todo
                                buildingSpawners.put(spawnTile, 0);
                            }
                        }
                    });

                    Events.on(EventType.BlockDestroyEvent.class, e -> {
                        Building spawner = e.tile.build;
                        if (spawner == null || spawner.team == state.rules.defaultTeam) return;

                        if (spawner.block instanceof PayloadBlock) buildingSpawners.remove(e.tile);
                        else if (spawner.block instanceof CoreBlock) coreSpawners.remove(e.tile.build);
                    });

                    Events.on(EventType.TileChangeEvent.class, e -> updateSpawnerPaths());

                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked) {
                            camera.bounds(Tmp.r1).grow(2 * tilesize);
                            Draw.z(Layer.fogOfWar + 1);

                            for (int i = 0; i < tileSeqs.length; i++) {
                                IntSeq tileSeq = tileSeqs[i];
                                int offset = ((i + 1) * 2 - colors.length) * 2;

                                drawPaths(tileSeq, Pal.gray, 3f, offset);
                                drawPaths(tileSeq, colors[i], 1f, offset);
                            }

                            Draw.color(Pal.remove, Mathf.sinDeg(Time.time * 3) * 0.3f + 0.4f);
                            Lines.stroke(2);

                            if(state.rules.randomWaveAI){
                                for (Building building : random) {
                                    if (building != null) {
                                        Fill.square(building.x, building.y, building.block.size * 4, 0);
                                        Lines.square(building.x, building.y, building.block.size * 4);
                                    }
                                }
                            }

                            Draw.reset();
                            Lines.stroke(1);
                        }
                    });
                }

                public void addSpawners(){
                    spawners.clear();
                    buildingSpawners.clear();
                    coreSpawners.clear();
                    for (IntSeq tileSeq : tileSeqs) {
                        tileSeq.clear();
                    }

                    if (!state.rules.pvp) {
                        PublicStaticVoids.eachGroundSpawn(-1, (t, b, c) -> {
                            if (b) spawners.add(t);
                            else coreSpawners.put(c, t);
                        });
                    }

                    updateSpawnerPaths();
                }

                public void drawPaths(IntSeq tileSeq, Color color, float thick, int offset) {
                    int lastTile = -1;

                    Lines.stroke(thick);
                    Draw.color(color, Mathf.sin(30, 0.4f) + 0.6f);

                    for (int j = 0; j < tileSeq.size; j++) {
                        int tile = tileSeq.get(j);

                        if(tile != -2) {
                            int x1 = (tile >>> 16) * 8 + offset, y1 = (tile & 0xFFFF) * 8 + offset,
                                    x2 = (lastTile >>> 16) * 8 + offset, y2 = (lastTile & 0xFFFF) * 8 + offset;

                            boolean containTo = Tmp.r1.contains(x1, y1), containFrom = Tmp.r1.contains(x2, y2);

                            if (tile == -1) {
                                if (containFrom) Lines.square(x2, y2, 7, 45);
                            } else if (lastTile < 0) {
                                if (containTo) Lines.square(x1, y1, 7, 45);
                            } else if (containFrom || containTo || Intersector.intersectSegmentRectangle(x1, y1, x2, y2, Tmp.r1)) {
                                Lines.line(x1, y1, x2, y2, false);
                            }
                        }

                        lastTile = tile;
                    }
                }

                public Tile spawnTile(Building spawner) {
                    int radius = (spawner.block instanceof UnitAssembler a ? a.areaSize : 0) + spawner.block.size * 4 + 1;
                    int ox = Geometry.d4x(spawner.rotation) * radius;
                    int oy = Geometry.d4y(spawner.rotation) * radius;

                    return world.tileWorld(spawner.x + ox, spawner.y + oy);
                }

                public void updateSpawnerPaths() {
                    for (IntSeq tileSeq : tileSeqs) {
                        tileSeq.clear();
                    }
                    Arrays.fill(random, null);

                    spawners.each(tile -> {
                        for (int i = 0; i < colors.length; i++) {
                            updatePaths(tile, i);
                        }
                    });

                    buildingSpawners.each(this::updatePaths);

                    coreSpawners.values().toSeq().each(tile -> {
                        for (int i = 0; i < colors.length; i++) {
                            updatePaths(tile, i);
                        }
                    });

                    if(state.rules.randomWaveAI){
                        //maximum amount of different target flag types they will attack
                        for (int i = 0; i < randomTargets.length; i++) {
                            BlockFlag target = randomTargets[i];
                            var targets = indexer.getEnemy(state.rules.waveTeam, target);
                            if (!targets.isEmpty()) {
                                for (Building other : targets) {
                                    if ((other.items != null && other.items.any()) || other.status() != BlockStatus.noInput) {
                                        random[i] = other;
                                    }
                                }
                            }
                        }
                    }
                }

                public void updatePaths(Tile tile, int type) {
                    IntSeq tiles = tileSeqs[type];
                    tiles.add(tile.pos());

                    tmp = tile;

                    while (true) {
                        Tile nextTile = Tools.copyPathfinder.getTargetTile(tmp, Tools.copyPathfinder.getField(type));

                        if((tmp.pos() != tiles.peek())
                                && Intersector.pointLineSide(tiles.peek() >>> 16, tiles.peek() & 0xFFFF, tmp.x, tmp.y, nextTile.x, nextTile.y) != 0){
                            tiles.add(tmp.pos());
                        }

                        if (tmp == nextTile) {
                            tiles.add(tmp.pos());
                            tiles.add(-1);
                            break;
                        } else if(tiles.contains(nextTile.pos())){
                            tiles.add(nextTile.pos());
                            tiles.add(-2);
                            break;
                        }

                        tmp = nextTile;
                    }
                }
            },

            new FunctionButton("拆除地图上所有的石头", Icon.spray, () -> {
                Unit unit = player.unit();
                if (unit == null) return;

                world.tiles.eachTile(tile -> {
                    Block block = tile.block();
                    if (block instanceof Prop && block.breakable) {
                        unit.plans.add(new BuildPlan(tile.x, tile.y));
                    }
                });
            }, null),

            new FunctionButton("拆除地图上所有的废墟", Icon.spray, () -> {
                if (player.unit() == null) return;
                state.teams.get(Team.derelict).buildings.each(b -> player.unit().plans.add(new BuildPlan(b.tileX(), b.tileY())));
            }, null),

            new FunctionButton("拆除地图上所有病毒逻辑和非玩家建造的潜在病毒逻辑", Icon.spray, () -> {
            }, null) {

                @Override
                public void init() {
                    check = () -> {
                        if(player.unit() == null)return;

                        state.teams.get(player.team()).buildings.each(b -> {
                            if (b instanceof ConstructBlock.ConstructBuild cb && cb.current instanceof LogicBlock
                                    && cb.lastConfig instanceof LogicBlock.LogicBuild lb && isVirus(lb)) {

                                player.unit().plans.add(new BuildPlan(b.tileX(), b.tileY()));
                            } else if ((b instanceof LogicBlock.LogicBuild lb && isVirus(lb))) {
                                player.unit().plans.add(new BuildPlan(b.tileX(), b.tileY()));
                                Fx.ripple.at(b.tileX(), b.tileY(), 40, Pal.heal);
                            }
                        });
                    };
                }

                public boolean isVirus(LogicBlock.LogicBuild lb) {
                    for (LExecutor.LInstruction instruction : LAssembler.assemble(lb.code, false).instructions) {
                        if (instruction instanceof LExecutor.UnitControlI uci && uci.type == LUnitControl.build) {
                            return true;
                        }
                    }
                    return false;
                }
            },

            new FunctionButton("显示屏幕内地图方格燃烧性", new TextureRegionDrawable(StatusEffects.burning.fullIcon), () -> {
            }) {
                @Override
                public void init() {
                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked) {
                            Draw.color(Items.pyratite.color);
                            PublicStaticVoids.eachCameraTiles(tile -> {
                                if (tile.getFlammability() == 0) return;

                                float a = (float) Math.atan(tile.getFlammability()) * 2 / Mathf.pi;

                                Draw.alpha(a);
                                Fill.square(tile.worldx(), tile.worldy(), 4);
                            });

                            Draw.reset();
                        }
                    });
                }
            },

            new FunctionButton("玩家单位自动挖取范围内紧缺的矿物", Icon.production, () -> {
            }) {
                final ObjectMap<Item, Tile> ores = new ObjectMap<>();

                @Override
                public void init() {
                    update = () -> {
                        Unit unit = player.unit();
                        CoreBlock.CoreBuild core = player.closestCore();

                        if (unit == null || core == null) return;
                        UnitType type = unit.type;

                        ores.clear();

                        float velAngle = unit.vel.angle();

                        for (int tx = 0; tx < type.mineRange * 2; tx += 8) {
                            for (int ty = 0; ty < type.mineRange * 2; ty += 8) {
                                Tile tile = world.tileWorld(unit.x - type.mineRange + tx, unit.y - type.mineRange + ty);

                                if (!unit.validMine(tile)) continue;

                                float angle = unit.angleTo(tile.worldx(), tile.worldy());
                                Item drop = unit.getMineResult(tile);
                                Tile best = ores.get(drop);

                                if (best == null) {
                                    ores.put(drop, tile);
                                } else {
                                    float tAngle = unit.angleTo(best.worldx(), best.worldy());
                                    if (Math.abs(angle - velAngle) < Math.abs(tAngle - velAngle)) {
                                        ores.put(drop, tile);
                                    }
                                }
                            }
                        }


                        if (unit.mineTile != null) {
                            Item mine = unit.getMineResult(unit.mineTile);
                            if (mine == null) {
                                unit.mineTile = null;
                                return;
                            }

                            if (unit.stack.amount >= unit.type.itemCapacity || core.items.get(mine) > Core.settings.getInt("自动挖矿阈值")) {
                                unit.mineTile = null;
                            }

                        }

                        if (unit.stack.amount > 0 && unit.within(core, mineTransferRange)) {
                            Call.transferItemTo(unit, unit.stack.item, unit.stack.amount, unit.x, unit.y, core);
                        }

                        if (unit.stack.amount == 0) {
                            for (Item item : ores.keys()) {
                                if (mine(item, core, unit)) break;
                            }
                        } else {
                            mine(unit.stack.item, core, unit);
                        }

                    };
                }

                public boolean mine(Item item, CoreBlock.CoreBuild core, Unit unit) {
                    if (core.items.get(item) < Core.settings.getInt("自动挖矿阈值") && item.buildable) {
                        Tile ore = ores.get(item);
                        if (ore != null) {
                            unit.mineTile(ore);
                            return true;
                        }
                    }
                    return false;
                }
            },

            new FunctionButton("将玩家设置到地图上点击的某个位置, \n无法瞬移的服务器在玩家14格外会有虚影显示玩家实际位置?", Icon.move) {
                final Vec2 checkedPos = new Vec2();
                final Vec2 playerPos = new Vec2();
                final Tile[] tiles = new Tile[2];
                float pr;
                {
                    saveable = false;
                }

                @Override
                public void init() {
                    update = () -> {
                        if(unit() == null)return;

                        if (!player.within(playerPos, tilesize * 14) && !player.within(checkedPos, tilesize)) {
                            playerPos.set(unit().x, unit().y);
                            pr = player.unit().rotation;
                        }

                        if (!checkedPos.isZero() && !unit().within(checkedPos, 0.01f)) {
                            if (!unit().type.canBoost && !unit().type.flying) {
                                tiles[1] = unit().tileOn();
                                World.raycastEachWorld(unit().x, unit().y, checkedPos.x, checkedPos.y, (x, y) -> {
                                    Tile tile = world.tile(x, y);

                                    if (tile == null || !unit().canPass(x, y)) {
                                        return true;
                                    }

                                    tiles[1] = tiles[0];
                                    tiles[0] = tile;

                                    return false;
                                });

                                Tile tile;

                                if (tiles[1] != null) tile = tiles[1];
                                else tile = tiles[0];

                                checkedPos.set(tile.worldx(), tile.worldy());
                            }
                            unit().set(checkedPos);
                        }
                    };

                    check = checkedPos::setZero;
                    checkOff = () -> {};

                    Events.on(EventType.WorldLoadEvent.class, e -> checkedPos.setZero());

                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked && Vars.netServer.admins.isStrict() && headless && Vars.net.active() && unit() != null) {
                            Draw.z(Layer.effect);
                            Draw.rect(unit().type.fullIcon, playerPos.x, playerPos.y, pr - 90);
                            Draw.reset();
                        }
                    });

                    Events.on(EventType.TapEvent.class, e -> {
                        if (checked && e.player == player) {
                            //if(e.tile != null)
                            checkedPos.set(e.tile.worldx(), e.tile.worldy());
                        }
                    });
                }

                private Unit unit() {
                    return player.unit();
                }
            },

            new FunctionButton("绘制建造列表的drawPlace", Icon.book){
                @Override
                public void init() {
                    update = () -> {};

                    Events.run(EventType.Trigger.draw, () -> {
                        if (!checked) return;

                        drawPlace(control.input.selectPlans);
                        drawPlace(control.input.linePlans);
                    });
                }

                void drawPlace(Seq<BuildPlan> plans){
                    plans.each(bp -> {
                        boolean valid = bp.block.canPlaceOn(world.tile(bp.x, bp.y), player.team(), bp.rotation);
                        bp.block.drawPlace(bp.x, bp.y, bp.rotation, valid);
                    });
                }
            },

            new FunctionButton("放置蓝图或者建筑时, 拆除建造列表下方阻挡的建筑", Icon.layers){
                final Seq<BuildPlan> tmpPlans = new Seq<>();
                final Seq<Building> breaks = new Seq<>();

                @Override
                public void init() {
                    Events.on(EventType.TapEvent.class, e -> {
                        if (e.player != Vars.player || !checked) return;

                        coverPlace(e.tile.x, e.tile.y);
                    });

                    Events.run(EventType.Trigger.draw, () -> {
                        tmpPlans.each(bp -> bp.block.drawPlan(bp, tmpPlans, true, (Mathf.sinDeg(Time.time * 15) + 3) / 4f));
                    });

                    check = () -> {};

                    checkOff = () -> {
                        tmpPlans.clear();
                        breaks.clear();
                    };

                    update = () -> {
                        if(tmpPlans.size > 0 && player.unit() != null && player.unit().plans.size == 0){
                            tmpPlans.each(p -> player.unit().plans.add(p));
                            tmpPlans.clear();
                        }
                    };
                }

                public void coverPlace(int spx, int spy) {
                    Tile tile = world.tile(spx, spy);
                    if(tile == null)return;

                    Unit unit = Vars.player.unit();
                    if(unit == null)return;

                    control.input.selectPlans.each(bp -> {
                        if (bp.build() != null && bp.build().block == bp.block && bp.build().tileX() == bp.x && bp.build().tileY() == bp.y) {
                            return;
                        }

                        breaks.clear();
                        bp.hitbox(Tmp.r1);

                        for (int i = 0; i < Tmp.r1.width / 8; i++) {
                            for (int j = 0; j < Tmp.r1.height / 8; j++) {
                                int x = (int) (Tmp.r1.x / 8 + i) + 1;
                                int y = (int) (Tmp.r1.y / 8 + j) + 1;

                                Building building = Vars.world.build(x, y);
                                Tile worldTile = world.tile(x, y);

                                if(worldTile == null)return;

                                //不拆除能被替换的建筑: 防止出现玩家建造能够替换的建筑时, 由于 被替换的建筑的拆除计划 仍然存在导致的建造列表错误
                                if (!canReplace(bp, worldTile)){
                                    if (building != null) {
                                        if (breaks.contains(building)) continue;
                                        breaks.addUnique(building);

                                        unit.plans.add(new BuildPlan(building.tileX(), building.tileY()));
                                    } else {
                                        unit.plans.add(new BuildPlan(x, y));
                                    }
                                }
                            }
                        }
                    });

                    control.input.selectPlans.each(bp -> tmpPlans.add(bp.copy()));
                }
            },

            new FunctionButton("暂停器, 用于暂停游戏", Icon.pause){
                {
                    saveable = false;
                }
                boolean shouldPause;
                @Override
                public void init() {
                    update = () -> {
                        if(shouldPause){
                            state.set(GameState.State.paused);
                            shouldPause = false;
                        }else if (state.isGame() && !state.isPaused()){
                            shouldPause = true;
                        }
                    };
                }
            },


//            new FunctionButton("坠落伤害测试器", Icon.link) {
//                float total;
//                int times;
//                final Interval interval = new Interval();
//                final UnitType type = UnitTypes.oct;
//                Unit poly;
//
//                @Override
//                public void init() {
//                    check = () -> total = times = 0;
//                    checkOff = () -> Log.info("--------------");
//
//                    update = () -> {
//                        Building building = world.build(100, 100);
//                        if(building == null){
//                            checked = false;
//                            return;
//                        }
//
//                        if(interval.get(15f)){
//                            poly = type.create(Team.crux);
//                            poly.set(building);
//                            poly.add();
//                            poly.stack.set(Items.surgeAlloy, type.itemCapacity);
//
//                            float radius = Mathf.pow(poly.hitSize, 0.94f) * 1.25f;
//
//                            int amount = PublicStaticVoids.completeDamage(poly.team, poly.x, poly.y, radius);
//                            float destory = Mathf.pow(poly.hitSize, 0.75f) * type.crashDamageMultiplier * 5f * state.rules.unitCrashDamage(poly.team);
//
//                            poly.destroy();
//
//                            float damage = building.maxHealth - building.health - amount * destory;
//                            if(!Mathf.equal(damage, 0, 0.1f)) {
//                                total += damage;
//                                times++;
//                                Log.info(total / times);
//                            }
//                            building.health = building.maxHealth;
//                        }
//                    };
//                }
//            },
    };

    float size = 46;
    int rowWidth = Mathf.floor(ShortcutsSchematicsTable.getTotalWidth() / size);

    public ButtonsTable(Table parents) {
        for (int i = 0; i < functionButtons.length; i++) {
            FunctionButton button = functionButtons[i];
            button.checked = Core.settings.getBool("tools-functions-" + i) && button.saveable;
        }

        parents.table(buttonsTable -> {
            int i = 0;
            for (FunctionButton function : functionButtons) {
                function.init();

                int j = i;
                ImageButton imageButton = buttonsTable.button(
                        function.icon,
                        !function.hasSwitch() ? Styles.clearNonei : Styles.clearNoneTogglei,
                        () -> {
                            if(function.check != null){
                                (function.checked ? function.checkOff : function.check).run();
                            }

                            if(function.hasSwitch()){
                                function.checked = !function.checked;
                                Core.settings.put("tools-functions-" + j, function.checked);
                            }

                        }).size(46).checked(b -> function.checked).tooltip(function.description).margin(10).get();
                imageButton.resizeImage(32);

                if (i % rowWidth == rowWidth - 1) buttonsTable.row();

                i++;
            }

            for (int j = 0; j < (rowWidth - i % rowWidth) % rowWidth; j++) {
                buttonsTable.add().size(size);
            }
        }).width(rowWidth * size);

        Events.run(EventType.Trigger.update, () -> {
            for (FunctionButton function : functionButtons) {
                if (function.update != null && function.checked) function.update.run();
            }
        });
    }


    public static class FunctionButton {
        public String description;
        public Drawable icon;
        public boolean checked;
        public Runnable update, check, checkOff;
        public boolean saveable = true;

        public FunctionButton(String description, Drawable icon) {
            this.description = description;
            this.icon = icon;
        }

        public FunctionButton(String description, Drawable icon, Runnable update) {
            this.description = description;
            this.icon = icon;
            this.update = update;
        }

        public FunctionButton(String description, Drawable icon, Runnable check, Runnable checkOff) {
            this.description = description;
            this.icon = icon;
            this.check = check;
            this.checkOff = checkOff;
        }

        public FunctionButton(String description, Drawable icon, Runnable update, Runnable check, Runnable checkOff) {
            this.description = description;
            this.icon = icon;
            this.update = update;
            this.check = check;
            this.checkOff = checkOff;
        }

        public void init() {
        }

        public boolean hasSwitch(){
            return checkOff != null || update != null;
        }
    }
}
