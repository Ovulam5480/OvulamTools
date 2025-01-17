package Tools.UI;

import Tools.PublicStaticVoids;
import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.FloatSeq;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.MinerAI;
import mindustry.content.*;
import mindustry.core.World;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.units.BuildPlan;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.input.DesktopInput;
import mindustry.input.MobileInput;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.storage.CoreBlock;

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
                public void on() {
                    Events.run(EventType.Trigger.draw, () -> {
                        if (checked) {
                            Draw.z(Layer.fogOfWar + 1f);
                            Drawf.dashRect(Pal.remove, world.getQuadBounds(Tmp.r1));
                            Draw.reset();
                        }
                    });

                    update = () -> {
                        if (Core.scene.hasField()) return;

                        Unit player = Vars.player.unit();
                        if (player == null) return;

                        pos.set(player.x, player.y);
                        float speed = player.speed() * 1.2f;

                        if (mobile) {
                            //todo 待测试
                            MobileInput input = (MobileInput) control.input;

                            movement.set(input.movement.x, input.movement.y).nor().scl(speed);
                        } else {
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

            new FunctionButton("拆除地图上所有的石头", Icon.eraser, () -> {
                Unit unit = player.unit();
                world.tiles.eachTile(tile -> {
                    Block block = tile.block();
                    if (block instanceof Prop && block.breakable) {
                        unit.plans.add(new BuildPlan(tile.x, tile.y));
                    }
                });
            }, null),

            new FunctionButton("拆除地图上所有的废墟", Icon.spray, () -> {
                world.tiles.eachTile(tile -> {
                    Building building = tile.build;
                    if (building != null && building.team == Team.derelict) {
                        player.unit().plans.add(new BuildPlan(tile.x, tile.y));
                    }
                });
            }, null),

            new FunctionButton("显示屏幕内地图方格燃烧性", new TextureRegionDrawable(StatusEffects.burning.fullIcon), () -> {
            }) {
                @Override
                public void on() {
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
                public void on() {
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

//                                float dst = unit.dst(tile.worldx(), tile.worldy());
//                                Item drop = unit.getMineResult(tile);
//
//                                Tile near = ores.get(drop);
//                                float minDst = near == null ? 999999 : unit.dst(near.worldx(), near.worldy());
//
//                                if(dst < minDst && unit.canMine()){
//                                    ores.put(drop, tile);
//                                }

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

                            if (unit.stack.amount > 0 && unit.within(core, mineTransferRange)) {
                                Call.transferItemTo(unit, unit.stack.item, unit.stack.amount, unit.x, unit.y, core);
                            }
                        }

                        if (unit.stack.amount == 0) {
                            for (Item item : ores.keys()) {
                                if(mine(item, core, unit))break;
                            }
                        } else {
                            mine(unit.stack.item, core, unit);
                        }

                    };
                }

                public boolean mine(Item item, CoreBlock.CoreBuild core, Unit unit){
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
                public void on() {
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
                public void on() {
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
                public void on() {
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
                function.on();

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

        public void on() {

        }
    }
}
