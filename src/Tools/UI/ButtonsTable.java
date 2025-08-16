package Tools.UI;

import Tools.OvulamTools;
import Tools.PathSpawners;
import Tools.UI.ShortcutsSchematics.ShortcutsSchematicsTable;
import Tools.copy.ClientPathfinder;
import Tools.copy.PublicStaticVoids;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.Pools;
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
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockStatus;

import java.util.Arrays;

import static Tools.type.SchematicPlacer.canReplace;
import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.world.meta.BlockFlag.*;
import static mindustry.world.meta.BlockFlag.drill;

public class ButtonsTable {
    FunctionButton lastChecked;

    private final FunctionButton[] functionButtons = new FunctionButton[]{
            new FunctionButton("玩家单位转圈圈", Icon.rotate){
                float rotateSpeed = settings.getInt("旋转速度倍率") / 100f;

                @Override
                public void init() {
                    update = () -> {
                        Unit unit = playerUnit();
                        if (unit == null) return;
                        unit.rotation = unit.type.rotateSpeed * unit.speedMultiplier * Time.time * rotateSpeed;
                    };
                }

                @Override
                public void buildSettings(SettingsMenuDialog.SettingsTable st) {
                    st.sliderPref("旋转速度倍率", 120, 50, 500, i -> {
                        rotateSpeed = i / 100f;
                        return i + "%";
                    });
                }
            },

            new FunctionButton("玩家单位无惯性移动", Icon.right, () -> {
            }) {
                final Vec2 movement = new Vec2(), pos = new Vec2();

                @Override
                public void init() {
                    draw = () -> {
                        Draw.z(Layer.fogOfWar + 1f);
                        if (state.rules.limitMapArea)
                            Tmp.r1.set(state.rules.limitX * tilesize, state.rules.limitY * tilesize, state.rules.limitWidth * tilesize, state.rules.limitHeight * tilesize).grow(finalWorldBounds * 2);
                        else world.getQuadBounds(Tmp.r1);

                        Drawf.dashRect(Pal.remove, Tmp.r1);
                        Draw.reset();
                    };

                    update = () -> {
                        Unit unit = playerUnit();
                        if (unit == null) return;

                        pos.set(unit.x, unit.y);
                        float speed = unit.speed() * 1.2f;

                        if (mobile) {
                            movement.set(((MobileInput) control.input).movement).nor().scl(speed);
                        } else if (!Core.scene.hasField()) {
                            movement.set(((DesktopInput) control.input).movement).nor().scl(speed);
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


            new FunctionButton("生存模式显示敌方陆军进攻路线", Icon.distribution, () -> {
            }) {
                final ArrayMap<Color, IntSeq> pathPoints = new ArrayMap<>(Color.class, IntSeq.class);
                ClientPathfinder pathfinder;
                PathSpawners spawners;

                private final BlockFlag[] randomTargets = {storage, generator, launchPad, factory, repair, battery, reactor, drill};
                final Building[] targets = new Building[randomTargets.length];
                Tile tmp;

                @Override
                public void init() {
                    pathfinder = OvulamTools.clientPathfinder = new ClientPathfinder(checked);
                    spawners = OvulamTools.pathSpawners = new PathSpawners();

                    Color[] colors = {Pal.ammo, Color.valueOf("bf92f9"), Liquids.water.color, Liquids.neoplasm.color, Pal.remove, Pal.accent};

                    for (Color color : colors) {
                        pathPoints.put(color, new IntSeq());
                    }

                    check = () -> {
                        pathfinder.setEnabled(true);
                        initSpawners();
                    };
                    checkOff = () -> pathfinder.setEnabled(false);

                    Events.on(EventType.WorldLoadEvent.class, e -> {
                        if(checked)initSpawners();
                    });

                    Events.on(ClientPathfinder.FinishedFrontier.class, e -> {
                        if(checked)updateAllPathPoints();
                    });

                    Events.on(EventType.TileChangeEvent.class, e -> {
                        if(checked){
                            updateAllPathPoints();

                            if (state.rules.randomWaveAI) {
                                Arrays.fill(targets, null);
                                for (int i = 0; i < randomTargets.length; i++) {
                                    BlockFlag target = randomTargets[i];
                                    var targets = indexer.getEnemy(state.rules.waveTeam, target);
                                    if (!targets.isEmpty()) {
                                        for (Building other : targets) {
                                            if ((other.items != null && other.items.any()) || other.status() != BlockStatus.noInput) {
                                                this.targets[i] = other;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    });

                    draw = () -> {
                        camera.bounds(Tmp.r1).grow(2 * tilesize);
                        Draw.z(Layer.fogOfWar + 1);

                        int i = 0;
                        for (ObjectMap.Entry<Color, IntSeq> entry : pathPoints) {
                            IntSeq tileSeq = entry.value;

                            int offsetx = Geometry.d8(i).x * 2;
                            int offsety = Geometry.d8(i).y * 2;

                            drawPaths(tileSeq, Pal.gray, 2f, offsetx, offsety);
                            drawPaths(tileSeq, entry.key, 0.65f, offsetx, offsety);

                            i++;
                        }

                        Draw.color(Pal.remove, Mathf.sinDeg(Time.time * 3) * 0.3f + 0.4f);
                        Lines.stroke(2);

                        if (state.rules.randomWaveAI) {
                            for (Building building : targets) {
                                if (building != null) {
                                    Fill.square(building.x, building.y, building.block.size * 4, 0);
                                    Lines.square(building.x, building.y, building.block.size * 4);
                                }
                            }
                        }

                        Draw.reset();
                        Lines.stroke(1);
                    };
                }

                public void initSpawners() {
                    spawners.init();
                    pathfinder.init();

                    updateAllPathPoints();
                }

                public void drawPaths(IntSeq tileSeq, Color color, float thick, int offsetx, int offsety) {
                    int lastTile = -1;

                    Lines.stroke(thick);
                    Draw.color(color, Mathf.sin(30, 0.4f) + 0.6f);

                    for (int j = 0; j < tileSeq.size; j++) {
                        int tile = tileSeq.get(j);

                        if (tile != -2) {
                            int x1 = (tile >>> 16) * 8 + offsetx, y1 = (tile & 0xFFFF) * 8 + offsety,
                                    x2 = (lastTile >>> 16) * 8 + offsetx, y2 = (lastTile & 0xFFFF) * 8 + offsety;

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

                public void updateAllPathPoints() {
                    pathPoints.forEach(e -> e.value.clear());

                    spawners.eachSpawnerTiles(this::updatePathPoints);
                }

                public void updatePathPoints(Tile tile, int type) {
                    IntSeq tiles = pathPoints.getValueAt(type);
                    if(type == 0 && tile.dangerous()){
                        Tile[] hasSafe = {null};

                        loop : for(int i = 1; i <= 3; i++){
                            for(int j = -i; j <= i; j++){
                                for(int k = -i; k <= i; k++){
                                    if(Math.abs(j) == i || Math.abs(k) == i){
                                        Tile t = tile.nearby(j, k);
                                        if(t != null && !t.dangerous()){
                                            hasSafe[0] = t;
                                            break loop;
                                        }
                                    }
                                }
                            }
                        }

                        if(hasSafe[0] != null) tile = hasSafe[0];
                        else return;
                    }

                    tiles.add(tile.pos());

                    tmp = tile;

                    while (true) {
                        Tile nextTile = pathfinder.getTargetTile(tmp, pathfinder.getField(type));

                        if ((tmp.pos() != tiles.peek())
                                && Intersector.pointLineSide(tiles.peek() >>> 16, tiles.peek() & 0xFFFF, tmp.x, tmp.y, nextTile.x, nextTile.y) != 0) {
                            tiles.add(tmp.pos());
                        }

                        if (tmp == nextTile) {
                            tiles.add(tmp.pos());
                            tiles.add(-1);
                            break;
                        } else if (tiles.contains(nextTile.pos())) {
                            tiles.add(nextTile.pos());
                            tiles.add(-2);
                            break;
                        }

                        tmp = nextTile;
                    }
                }

                @Override
                public void buildSettings(SettingsMenuDialog.SettingsTable st) {
                    st.sliderPref("算法最大计算时间", 16, 1, 100, 1, i -> {
                        pathfinder.fleshTime = i * 100000;

                        return (i / 10f) + "ms";
                    });
                    st.sliderPref("寻路代价类型", 1, 0, 5, i -> {
                        if(!spawners.hasType[i]){
                            return "波次中不存在使用该类寻路代价的敌人";
                        }

                        pathfinder.drawValueType = i;
                        return switch (i) {
                            case 0 -> "陆军单位";
                            case 1 -> "爬行单位";
                            case 2 -> "海军单位";
                            case 3 -> "瘤虫单位";
                            case 4 -> "空军单位";
                            case 5 -> "悬浮单位";
                            default -> "其他";
                        };
                    });
                    st.checkPref("流场数据可视化", false, b -> pathfinder.drawValue = b);
                    st.checkPref("流场数据直显", false, b -> pathfinder.printValue = b);
                }

                @Override
                public void buildTable(Table table) {
                    table.add(new Element(){
                        @Override
                        public void draw() {
                            Font font = Fonts.outline;
                            GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
                            String text1 = "呱呱呱呱呱呱呱呱呱呱: 100000 \n \n \n";
                            lay.setText(font, text1);

                            int length = pathfinder.getField(pathfinder.drawValueType).frontier.size;
                            String text = "该类型剩余待更新边界: " + length + "\n上一帧新增: " + pathfinder.addedFrontier + "\n" + "遍历轮次: " + pathfinder.getField(pathfinder.drawValueType).search;

                            float red = Mathf.clamp(length / 4000f - 0.4f, 0, 1);
                            font.setColor(1, 1 - red, 1 - red, 1f);
                            font.getCache().clear();
                            font.getCache().addText(text, x + width / 2f - lay.width / 2f, y + height / 2f + lay.height / 2f - 7f);
                            font.getCache().draw(parentAlpha);

                            font.setColor(1, 1, 1, 1f);
                            Pools.free(lay);
                        }
                    }).height(40f);

                    table.marginTop(10).marginBottom(10);
                }
            },

            new FunctionButton("拆除地图上所有的石头", Icon.spray, () -> {
                Unit unit = playerUnit();
                if (unit == null) return;

                world.tiles.eachTile(tile -> {
                    Block block = tile.block();
                    if (block instanceof Prop && block.breakable) {
                        unit.plans.add(new BuildPlan(tile.x, tile.y));
                    }
                });
            }, null),

            new FunctionButton("拆除地图上所有的废墟", Icon.spray, () -> {
                if (playerUnit() == null) return;
                state.teams.get(Team.derelict).buildings.each(b -> playerUnit().plans.add(new BuildPlan(b.tileX(), b.tileY())));
            }, null),

            new FunctionButton("拆除地图上所有病毒逻辑和非玩家建造的潜在病毒逻辑", Icon.spray, () -> {
            }, null) {

                @Override
                public void init() {
                    check = () -> {
                        if (playerUnit() == null) return;

                        state.teams.get(player.team()).buildings.each(b -> {
                            if (b instanceof ConstructBlock.ConstructBuild cb && cb.current instanceof LogicBlock
                                    && cb.lastConfig instanceof LogicBlock.LogicBuild lb && isVirus(lb)) {

                                playerUnit().plans.add(new BuildPlan(b.tileX(), b.tileY()));
                            } else if ((b instanceof LogicBlock.LogicBuild lb && isVirus(lb))) {
                                playerUnit().plans.add(new BuildPlan(b.tileX(), b.tileY()));
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
                    draw = () -> {
                        Draw.color(Items.pyratite.color);
                        PublicStaticVoids.eachCameraTiles(tile -> {
                            if (tile.getFlammability() == 0) return;

                            float a = (float) Math.atan(tile.getFlammability()) * 2 / Mathf.pi;

                            Draw.alpha(a);
                            Fill.square(tile.worldx(), tile.worldy(), 4);
                        });

                        Draw.reset();
                    };
                }
            },

            new FunctionButton("玩家单位自动挖取范围内紧缺的矿物", Icon.production, () -> {
            }) {
                final ObjectMap<Item, Tile> ores = new ObjectMap<>();

                @Override
                public void init() {
                    update = () -> {
                        Unit unit = playerUnit();
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

                @Override
                public void buildSettings(SettingsMenuDialog.SettingsTable st) {
                    st.sliderPref("自动挖矿阈值", 1000, 100, 3500, 100, i -> i + "物品");
                }
            },

            //todo 核心瞬移
            new FunctionButton("将玩家设置到地图上点击的某个位置, \n注意服务器无法瞬移", Icon.move) {
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
                        Unit unit = playerUnit();

                        if (unit == null) return;

                        if (!player.within(playerPos, tilesize * 14) && !player.within(checkedPos, tilesize)) {
                            playerPos.set(unit.x, unit.y);
                            pr = unit.rotation;
                        }

                        if (!checkedPos.isZero() && !unit.within(checkedPos, 0.01f)) {
                            if (!unit.type.canBoost && !unit.type.flying) {
                                tiles[1] = unit.tileOn();
                                World.raycastEachWorld(unit.x, unit.y, checkedPos.x, checkedPos.y, (x, y) -> {
                                    Tile tile = world.tile(x, y);

                                    if (tile == null || !unit.canPass(x, y)) {
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
                            unit.set(checkedPos);
                        }
                    };

                    check = checkedPos::setZero;
                    checkOff = () -> {
                    };

                    Events.on(EventType.WorldLoadEvent.class, e -> checkedPos.setZero());

                    draw = () -> {
                        if (Vars.netServer.admins.isStrict() && headless && Vars.net.active() && playerUnit() != null) {
                            Draw.z(Layer.effect);
                            Draw.rect(playerUnit().type.fullIcon, playerPos.x, playerPos.y, pr - 90);
                            Draw.reset();
                        }
                    };

                    Events.on(EventType.TapEvent.class, e -> {
                        if (checked && e.player == player) {
                            //if(e.tile != null)
                            checkedPos.set(e.tile.worldx(), e.tile.worldy());
                        }
                    });
                }
            },

            new FunctionButton("放置提示显示", Icon.book) {
                final FrameBuffer buffer = new FrameBuffer();
                float alpha = settings.getInt("绘制透明度") / 100f;

                @Override
                public void init() {
                    update = () -> {
                    };

                    draw = () -> {
                        buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                        Draw.draw(Layer.flyingUnit + 1, () -> {
                            buffer.begin(Color.clear);
                            drawPlaces(control.input.selectPlans);
                            drawPlaces(control.input.linePlans);
                            buffer.end();

                            Tmp.tr1.set(Draw.wrap(buffer.getTexture()));
                            Tmp.tr1.flip(false, true);

                            Draw.scl(4 / Vars.renderer.getDisplayScale());

                            Draw.mixcol(Color.white, 0.24f + Mathf.absin(Time.globalTime, 6f, 0.28f));
                            Draw.alpha(alpha);
                            Draw.rect(Tmp.tr1, camera.position.x, camera.position.y);
                            Draw.reset();
                        });
                    };
                }

                void drawPlaces(Seq<BuildPlan> plans) {
                    plans.each(bp -> {
                        Tile tile = world.tile(bp.x, bp.y);

                        if(tile != null) {
                            boolean valid = bp.block.canPlaceOn(tile, player.team(), bp.rotation);
                            bp.block.drawPlace(bp.x, bp.y, bp.rotation, valid);
                        }
                    });
                }

                @Override
                public void buildSettings(SettingsMenuDialog.SettingsTable st) {
                    st.sliderPref("绘制透明度", 100, 0, 100, i -> {
                        alpha = i / 100f;
                        return i + "%";
                    });
                }
            },

            new FunctionButton("放置蓝图或者建筑时, 拆除建造列表下方阻挡的建筑", Icon.layers) {
                final Seq<BuildPlan> tmpPlans = new Seq<>();
                final Seq<Building> breaks = new Seq<>();

                @Override
                public void init() {
                    Events.on(EventType.TapEvent.class, e -> {
                        if (e.player != Vars.player || !checked) return;

                        coverPlace(e.tile.x, e.tile.y);
                    });

                    check = () -> {
                    };

                    checkOff = () -> {
                        tmpPlans.clear();
                        breaks.clear();
                    };

                    update = () -> {
                        if (tmpPlans.size > 0 && playerUnit() != null && playerUnit().plans.size == 0) {
                            tmpPlans.each(p -> playerUnit().plans.add(p));
                            tmpPlans.clear();
                        }
                    };

                    draw = () -> tmpPlans.each(bp -> bp.block.drawPlan(bp, tmpPlans, true, (Mathf.sinDeg(Time.time * 15) + 3) / 4f));
                }

                public void coverPlace(int spx, int spy) {
                    Tile tile = world.tile(spx, spy);
                    if (tile == null) return;

                    if (playerUnit() == null) return;

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

                                if (worldTile == null) return;

                                //不拆除能被替换的建筑: 防止出现玩家建造能够替换的建筑时, 由于 被替换的建筑的拆除计划 仍然存在导致的建造列表错误
                                if (!canReplace(bp, worldTile)) {
                                    if (building != null) {
                                        if (breaks.contains(building)) continue;
                                        breaks.addUnique(building);

                                        playerUnit().plans.add(new BuildPlan(building.tileX(), building.tileY()));
                                    } else {
                                        playerUnit().plans.add(new BuildPlan(x, y));
                                    }
                                }
                            }
                        }
                    });

                    control.input.selectPlans.each(bp -> tmpPlans.add(bp.copy()));
                }
            },

            new FunctionButton("暂停器, 用于暂停游戏", Icon.pause) {
                boolean shouldPause;

                {
                    saveable = false;
                }

                @Override
                public void init() {
                    update = () -> {
                        if (shouldPause) {
                            state.set(GameState.State.paused);
                            shouldPause = false;
                        } else if (state.isGame() && !state.isPaused()) {
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
        SettingsMenuDialog.SettingsTable settingsTable = new SettingsMenuDialog.SettingsTable(){
            final Cell empty = new Cell<>();

            @Override
            public void rebuild(){
                clear();

                for(Setting setting : list){
                    setting.add(this);
                }
            }

            @Override
            public <T extends Element> Cell<T> add(T element) {
                if(element.getClass() == TextButton.class){
                    return empty;
                }
                return super.add(element);
            }
        };

        Table table = new Table();

        parents.add(table).marginLeft(18).marginRight(18).row();
        parents.add(settingsTable).marginBottom(20f).marginLeft(18).row();

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
                            if (function.check != null) {
                                (function.checked ? function.checkOff : function.check).run();
                            }

                            if (function.hasSwitch()) {
                                function.checked = !function.checked;
                                Core.settings.put("tools-functions-" + j, function.checked);

                                settingsTable.getSettings().clear();
                                table.clear();
                                table.marginTop(0).marginBottom(0);

                                if(function.checked) {
                                    function.buildSettings(settingsTable);
                                    function.buildTable(table);
                                }

                                settingsTable.rebuild();
                            }

                        }).size(46).checked(b -> function.checked).margin(10).get();
                imageButton.resizeImage(32);
                ui.addDescTooltip(imageButton, function.description);

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

        Events.run(EventType.Trigger.draw, () -> {
            for (FunctionButton function : functionButtons) {
                if (function.draw != null && function.checked) function.draw.run();
            }
        });
    }

    public static Unit playerUnit(){
        return player.unit();
    }

    public static class FunctionButton {
        public String description;
        public Drawable icon;
        public boolean checked;
        public Runnable update, draw, check, checkOff;
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

        public boolean hasSwitch() {
            return checkOff != null || update != null;
        }

        public void buildSettings(SettingsMenuDialog.SettingsTable st){
        }

        public void buildTable(Table table){
        }
    }
}
