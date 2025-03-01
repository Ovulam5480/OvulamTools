package Tools.UI;

import Tools.PublicStaticVoids;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.MobileInput;
import mindustry.logic.LAssembler;
import mindustry.logic.LExecutor;
import mindustry.logic.LUnitControl;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.payloads.PayloadBlock;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.UnitAssembler;

import static arc.Core.camera;
import static mindustry.Vars.*;

public class ButtonsTable {
    private final FunctionButton[] functionButtons = new FunctionButton[]{
            new FunctionButton("让玩家单位转圈圈", Icon.rotate, () -> {
                Unit unit = player.unit();
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
                        Unit player = Vars.player.unit();
                        if (player == null) return;

                        pos.set(player.x, player.y);
                        float speed = player.speed() * 1.2f;

                        if (mobile) {
                            //todo 待测试?
                            MobileInput input = (MobileInput) control.input;

                            movement.set(input.movement.x, input.movement.y).nor().scl(speed);
                        } else if (!Core.scene.hasField()) {
                            float xa = Core.input.axis(Binding.move_x);
                            float ya = Core.input.axis(Binding.move_y);

                            movement.set(xa, ya).nor().scl(speed);
                        }
                        pos.add(movement);

                        if (player.canPass(World.toTile(pos.x), World.toTile(pos.y))) {
                            player.vel.set(movement.x, movement.y);

                            if (!movement.isZero()) {
                                float a = movement.angle() - player.rotation;
                                player.rotation(movement.angle());

                                for (WeaponMount mount : player.mounts) {
                                    mount.rotation -= a;
                                }
                            }
                        }
                    };
                }
            },

            new FunctionButton("显示敌方陆军路线, 仅服务端有效", Icon.distribution, () -> {
            }) {
                final Color[] colors = {Pal.ammo, Pal.suppress, Liquids.water.color};
                final Seq<Tile> spawners = new Seq<>();
                final ObjectMap<Tile, Integer> buildingSpawners = new ObjectMap<>();
                final ObjectMap<Building, Tile> coreSpawners = new ObjectMap<>();
                Tile tmp;

                @Override
                public void init() {
                    Events.on(EventType.WorldLoadEvent.class, e -> {
                        spawners.clear();
                        buildingSpawners.clear();
                        coreSpawners.clear();

                        //仅服务端有效
                        if ((!net.active() || net.server())) {
                            if (state.rules.waves) {
                                PublicStaticVoids.eachGroundSpawn(-1, (t, b, c) -> {
                                    if (b) spawners.add(t);
                                    else coreSpawners.put(c, t);
                                });
                            }
                        } else {
                            checked = false;
                        }
                    });

                    Events.on(EventType.UnitCreateEvent.class, e -> {
                        if (state.rules.waves) {
                            if (!e.unit.isGrounded() || e.unit.team != player.team() || e.spawner == null || e.spawner.team == state.rules.defaultTeam)
                                return;

                            Tile spawnTile = spawnTile(e.spawner);

                            if (spawnTile != null && !spawnTile.solid()) {
                                buildingSpawners.put(spawnTile, e.unit.pathType());
                            }
                        }
                    });

                    Events.on(EventType.BlockDestroyEvent.class, e -> {
                        if (state.rules.waves) {
                            if (e.tile.build.block instanceof PayloadBlock) {
                                buildingSpawners.remove(spawnTile(e.tile.build));
                            }

                            if (state.rules.attackMode && e.tile.build instanceof CoreBlock.CoreBuild cb) {
                                coreSpawners.remove(cb);
                            }
                        }
                    });

                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked) {

                            spawners.each(tile -> {
                                for (int i = 0; i < colors.length; i++) {
                                    drawPathFinder(tile, i);
                                }
                            });

                            buildingSpawners.each(this::drawPathFinder);

                            coreSpawners.values().toSeq().each(tile -> {
                                for (int i = 0; i < colors.length; i++) {
                                    drawPathFinder(tile, i);
                                }
                            });

                        }
                    });
                }

                public Tile spawnTile(Building spawner) {
                    int radius = (spawner.block instanceof UnitAssembler a ? a.areaSize : 0) + spawner.block.size * 4 + 1;
                    int ox = Geometry.d4x(spawner.rotation) * radius;
                    int oy = Geometry.d4y(spawner.rotation) * radius;

                    return world.tileWorld(spawner.x + ox, spawner.y + oy);
                }

                public void drawPathFinder(Tile tile, int type) {
                    tmp = tile;
                    float offset = ((type + 1) * 2 - colors.length) * 1.4f;

                    Drawf.square(tmp.worldx() + offset, tmp.worldy() + offset, 6f, colors[type]);

                    while (true) {
                        Tile nextTile = Vars.pathfinder.getTargetTile(tmp, Vars.pathfinder.getField(Vars.state.rules.waveTeam, type, 0));
                        if (tmp == nextTile) {
                            Drawf.square(tmp.worldx() + offset, tmp.worldy() + offset, 6f, colors[type]);
                            break;
                        }

                        Draw.z(Layer.fogOfWar + 1);
                        Drawf.dashLine(colors[type], tmp.worldx() + offset, tmp.worldy() + offset, nextTile.worldx() + offset, nextTile.worldy() + offset);
                        tmp = nextTile;
                    }

                }
            },

            new FunctionButton("拆除地图上所有的石头", Icon.spray, () -> {
                Unit unit = player.unit();
                world.tiles.eachTile(tile -> {
                    Block block = tile.block();
                    if (block instanceof Prop && block.breakable) {
                        unit.plans.add(new BuildPlan(tile.x, tile.y));
                    }
                });
            }, null),

            new FunctionButton("拆除地图上所有的废墟", Icon.spray, () -> {
                state.teams.get(Team.derelict).buildings.each(b -> player.unit().plans.add(new BuildPlan(b.tileX(), b.tileY())));
            }, null),

            new FunctionButton("拆除地图上所有病毒逻辑和非玩家建造的潜在病毒逻辑", Icon.spray, () -> {
            }, null) {
                private final Seq<Building> dangerous = new Seq<>();

                @Override
                public void init() {
                    Events.on(EventType.WorldLoadEvent.class, e -> dangerous.clear());

                    Events.on(EventType.BlockBuildEndEvent.class, e -> {
                        if (!e.breaking) return;
                        if (state.rules.logicUnitBuild && e.tile.build instanceof LogicBlock.LogicBuild lb && isVirus(lb)) {
                            if (e.unit.isPlayer())
                                Call.sendChatMessage(lb.lastAccessed + "[white]建造了位于" + "(" + lb.tileX() + "," + lb.tileY() + ")" + "的病毒逻辑");
                            if (net.server()) {
                                lb.remove();
                                Call.sendChatMessage("已移除该逻辑");
                            }
                        }

                        if (state.rules.reactorExplosions && e.tile.build instanceof NuclearReactor.NuclearReactorBuild nr) {
                            for (CoreBlock.CoreBuild core : state.teams.get(player.team()).cores) {
                                float dst = core.dst(e.tile);
                                if (dst < 22 * 8) {
                                    dangerous.add(nr);
                                    Call.sendChatMessage(nr.lastAccessed + "[white]建造了位于" + "(" + nr.tileX() + "," + nr.tileY() + ")" + "的危险钍反");
                                    break;
                                }
                            }
                        }
                    });

                    check = () -> {
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
                            camera.bounds(Tmp.r1).grow(2 * tilesize);
                            Tmp.r2.set(0, 0, (world.width() - 1) * tilesize, (world.height() - 1) * tilesize);

                            Intersector.intersectRectangles(Tmp.r1, Tmp.r2, Tmp.r3);

                            Draw.color(Items.pyratite.color);

                            for (int i = 0; i < Tmp.r3.width; i = i + tilesize) {
                                for (int j = 0; j < Tmp.r3.height; j = j + tilesize) {
                                    Tile tile = world.tileWorld(Tmp.r3.x + i, Tmp.r3.y + j);

                                    if (tile.getFlammability() == 0) continue;

                                    float a = (float) Math.atan(tile.getFlammability()) * 2 / Mathf.pi;

                                    Draw.alpha(a);
                                    Fill.square(tile.worldx(), tile.worldy(), 4);
                                }
                            }
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

                @Override
                public void init() {
                    update = () -> {
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

                    checkOff = checkedPos::setZero;
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

            new FunctionButton("todo 敌对单位工厂计数器", Icon.eye) {
                final ObjectMap<Building, Integer> map = new ObjectMap<>();

                @Override
                public void init() {
                    checkOff = map::clear;

                    Events.on(EventType.UnitCreateEvent.class, e -> {
                        if (e.spawner != null && e.spawner.team != player.team()) {
                            map.put(e.spawner, map.get(e.spawner, () -> 0) + 1);
                        }
                    });

                    Font font = Fonts.outline;
                    Events.run(EventType.Trigger.draw, () -> {
                        if (!checked) return;
                        map.each((b, i) -> {
                            if (b != null) {
                                font.draw(i + "", b.x, b.y);
                            }
                        });
                    });
                }
            },

            new FunctionButton("让本地游戏无法附身的单位允许附身", Icon.link) {
                final Seq<UnitType> types = new Seq<>();

                @Override
                public void init() {
                    types.selectFrom(content.units(), t -> !t.playerControllable);

                    check = () -> types.each(t -> t.playerControllable = true);
                    checkOff = () -> types.each(t -> t.playerControllable = false);
                }
            },
    };

    float size = 46;
    int rowWidth = 6;
    float maxWidth = 288;

    public ButtonsTable(Table parents) {
        parents.table(buttonsTable -> {
            int i = 0;
            for (FunctionButton function : functionButtons) {
                function.init();

                ImageButton imageButton = buttonsTable.button(
                        function.icon,
                        function.checkOff == null ? Styles.clearNonei : Styles.clearNoneTogglei,
                        () -> {
                            if (function.checkOff == null) {
                                function.check.run();
                            } else {
                                function.checked = !function.checked;

                                if (function.checked) function.check.run();
                                else function.checkOff.run();
                            }
                        }).size(46).update(b -> b.setChecked(function.checked)).tooltip(function.description).margin(10).get();
                imageButton.resizeImage(32);

                if (i % rowWidth == rowWidth - 1) buttonsTable.row();

                i++;
            }

            for (int j = 0; j < (rowWidth - i % rowWidth) % rowWidth; j++) {
                buttonsTable.add().size(size);
            }
        }).width(maxWidth);

        Events.run(EventType.Trigger.update, () -> {
            for (FunctionButton function : functionButtons) {
                if (function.checked) function.update.run();
            }
        });
    }


    public float getOneDecimal(float f) {
        return (float) Mathf.floor(f * 10) / 10;
    }

    public static class FunctionButton {
        public String description;
        public Drawable icon;
        public boolean checked;
        public Runnable update, check, checkOff;

        public FunctionButton(String description, Drawable icon) {
            this.description = description;
            this.icon = icon;
            update = check = () -> {
            };
        }

        public FunctionButton(String description, Drawable icon, Runnable update) {
            this.description = description;
            this.icon = icon;
            this.update = update;
            check = checkOff = () -> {
            };
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
    }
}
