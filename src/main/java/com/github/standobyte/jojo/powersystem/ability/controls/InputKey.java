package com.github.standobyte.jojo.powersystem.ability.controls;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

public class InputKey implements InputBindTemplate {
    public static final InputKey LMB = new InputKey(InputType.MOUSE, "key.mouse.left", 0);
    public static final InputKey RMB = new InputKey(InputType.MOUSE, "key.mouse.right", 1);
    public static final InputKey MMB = new InputKey(InputType.MOUSE, "key.mouse.middle", 2);
    public static final InputKey MB4 = new InputKey(InputType.MOUSE, "key.mouse.4", 3);
    public static final InputKey MB5 = new InputKey(InputType.MOUSE, "key.mouse.5", 4);
    public static final InputKey MB6 = new InputKey(InputType.MOUSE, "key.mouse.6", 5);
    public static final InputKey MB7 = new InputKey(InputType.MOUSE, "key.mouse.7", 6);
    public static final InputKey MB8 = new InputKey(InputType.MOUSE, "key.mouse.8", 7);
    public static final InputKey _0 = new InputKey(InputType.KEYBOARD, "key.keyboard.0", 48);
    public static final InputKey _1 = new InputKey(InputType.KEYBOARD, "key.keyboard.1", 49);
    public static final InputKey _2 = new InputKey(InputType.KEYBOARD, "key.keyboard.2", 50);
    public static final InputKey _3 = new InputKey(InputType.KEYBOARD, "key.keyboard.3", 51);
    public static final InputKey _4 = new InputKey(InputType.KEYBOARD, "key.keyboard.4", 52);
    public static final InputKey _5 = new InputKey(InputType.KEYBOARD, "key.keyboard.5", 53);
    public static final InputKey _6 = new InputKey(InputType.KEYBOARD, "key.keyboard.6", 54);
    public static final InputKey _7 = new InputKey(InputType.KEYBOARD, "key.keyboard.7", 55);
    public static final InputKey _8 = new InputKey(InputType.KEYBOARD, "key.keyboard.8", 56);
    public static final InputKey _9 = new InputKey(InputType.KEYBOARD, "key.keyboard.9", 57);
    public static final InputKey A = new InputKey(InputType.KEYBOARD, "key.keyboard.a", 65);
    public static final InputKey B = new InputKey(InputType.KEYBOARD, "key.keyboard.b", 66);
    public static final InputKey C = new InputKey(InputType.KEYBOARD, "key.keyboard.c", 67);
    public static final InputKey D = new InputKey(InputType.KEYBOARD, "key.keyboard.d", 68);
    public static final InputKey E = new InputKey(InputType.KEYBOARD, "key.keyboard.e", 69);
    public static final InputKey F = new InputKey(InputType.KEYBOARD, "key.keyboard.f", 70);
    public static final InputKey G = new InputKey(InputType.KEYBOARD, "key.keyboard.g", 71);
    public static final InputKey H = new InputKey(InputType.KEYBOARD, "key.keyboard.h", 72);
    public static final InputKey I = new InputKey(InputType.KEYBOARD, "key.keyboard.i", 73);
    public static final InputKey J = new InputKey(InputType.KEYBOARD, "key.keyboard.j", 74);
    public static final InputKey K = new InputKey(InputType.KEYBOARD, "key.keyboard.k", 75);
    public static final InputKey L = new InputKey(InputType.KEYBOARD, "key.keyboard.l", 76);
    public static final InputKey M = new InputKey(InputType.KEYBOARD, "key.keyboard.m", 77);
    public static final InputKey N = new InputKey(InputType.KEYBOARD, "key.keyboard.n", 78);
    public static final InputKey O = new InputKey(InputType.KEYBOARD, "key.keyboard.o", 79);
    public static final InputKey P = new InputKey(InputType.KEYBOARD, "key.keyboard.p", 80);
    public static final InputKey Q = new InputKey(InputType.KEYBOARD, "key.keyboard.q", 81);
    public static final InputKey R = new InputKey(InputType.KEYBOARD, "key.keyboard.r", 82);
    public static final InputKey S = new InputKey(InputType.KEYBOARD, "key.keyboard.s", 83);
    public static final InputKey T = new InputKey(InputType.KEYBOARD, "key.keyboard.t", 84);
    public static final InputKey U = new InputKey(InputType.KEYBOARD, "key.keyboard.u", 85);
    public static final InputKey V = new InputKey(InputType.KEYBOARD, "key.keyboard.v", 86);
    public static final InputKey W = new InputKey(InputType.KEYBOARD, "key.keyboard.w", 87);
    public static final InputKey X = new InputKey(InputType.KEYBOARD, "key.keyboard.x", 88);
    public static final InputKey Y = new InputKey(InputType.KEYBOARD, "key.keyboard.y", 89);
    public static final InputKey Z = new InputKey(InputType.KEYBOARD, "key.keyboard.z", 90);
    public static final InputKey F1 = new InputKey(InputType.KEYBOARD, "key.keyboard.f1", 290);
    public static final InputKey F2 = new InputKey(InputType.KEYBOARD, "key.keyboard.f2", 291);
    public static final InputKey F3 = new InputKey(InputType.KEYBOARD, "key.keyboard.f3", 292);
    public static final InputKey F4 = new InputKey(InputType.KEYBOARD, "key.keyboard.f4", 293);
    public static final InputKey F5 = new InputKey(InputType.KEYBOARD, "key.keyboard.f5", 294);
    public static final InputKey F6 = new InputKey(InputType.KEYBOARD, "key.keyboard.f6", 295);
    public static final InputKey F7 = new InputKey(InputType.KEYBOARD, "key.keyboard.f7", 296);
    public static final InputKey F8 = new InputKey(InputType.KEYBOARD, "key.keyboard.f8", 297);
    public static final InputKey F9 = new InputKey(InputType.KEYBOARD, "key.keyboard.f9", 298);
    public static final InputKey F10 = new InputKey(InputType.KEYBOARD, "key.keyboard.f10", 299);
    public static final InputKey F11 = new InputKey(InputType.KEYBOARD, "key.keyboard.f11", 300);
    public static final InputKey F12 = new InputKey(InputType.KEYBOARD, "key.keyboard.f12", 301);
    public static final InputKey F13 = new InputKey(InputType.KEYBOARD, "key.keyboard.f13", 302);
    public static final InputKey F14 = new InputKey(InputType.KEYBOARD, "key.keyboard.f14", 303);
    public static final InputKey F15 = new InputKey(InputType.KEYBOARD, "key.keyboard.f15", 304);
    public static final InputKey F16 = new InputKey(InputType.KEYBOARD, "key.keyboard.f16", 305);
    public static final InputKey F17 = new InputKey(InputType.KEYBOARD, "key.keyboard.f17", 306);
    public static final InputKey F18 = new InputKey(InputType.KEYBOARD, "key.keyboard.f18", 307);
    public static final InputKey F19 = new InputKey(InputType.KEYBOARD, "key.keyboard.f19", 308);
    public static final InputKey F20 = new InputKey(InputType.KEYBOARD, "key.keyboard.f20", 309);
    public static final InputKey F21 = new InputKey(InputType.KEYBOARD, "key.keyboard.f21", 310);
    public static final InputKey F22 = new InputKey(InputType.KEYBOARD, "key.keyboard.f22", 311);
    public static final InputKey F23 = new InputKey(InputType.KEYBOARD, "key.keyboard.f23", 312);
    public static final InputKey F24 = new InputKey(InputType.KEYBOARD, "key.keyboard.f24", 313);
    public static final InputKey F25 = new InputKey(InputType.KEYBOARD, "key.keyboard.f25", 314);
    public static final InputKey NUM_LOCK = new InputKey(InputType.KEYBOARD, "key.keyboard.num.lock", 282);
    public static final InputKey NUM_0 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.0", 320);
    public static final InputKey NUM_1 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.1", 321);
    public static final InputKey NUM_2 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.2", 322);
    public static final InputKey NUM_3 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.3", 323);
    public static final InputKey NUM_4 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.4", 324);
    public static final InputKey NUM_5 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.5", 325);
    public static final InputKey NUM_6 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.6", 326);
    public static final InputKey NUM_7 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.7", 327);
    public static final InputKey NUM_8 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.8", 328);
    public static final InputKey NUM_9 = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.9", 329);
    public static final InputKey NUM_ADD = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.add", 334);
    public static final InputKey NUM_DECIMAL = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.decimal", 330);
    public static final InputKey NUM_ENTER = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.enter", 335);
    public static final InputKey NUM_EQUAL = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.equal", 336);
    public static final InputKey NUM_PLUS = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.multiply", 332);
    public static final InputKey NUM_DIVIDE = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.divide", 331);
    public static final InputKey NUM_MINUX = new InputKey(InputType.KEYBOARD, "key.keyboard.keypad.subtract", 333);
    public static final InputKey DOWN = new InputKey(InputType.KEYBOARD, "key.keyboard.down", 264);
    public static final InputKey LEFT = new InputKey(InputType.KEYBOARD, "key.keyboard.left", 263);
    public static final InputKey RIGHT = new InputKey(InputType.KEYBOARD, "key.keyboard.right", 262);
    public static final InputKey UP = new InputKey(InputType.KEYBOARD, "key.keyboard.up", 265);
    public static final InputKey APOSTROPHE = new InputKey(InputType.KEYBOARD, "key.keyboard.apostrophe", 39);
    public static final InputKey BACKSLASH = new InputKey(InputType.KEYBOARD, "key.keyboard.backslash", 92);
    public static final InputKey COMMA = new InputKey(InputType.KEYBOARD, "key.keyboard.comma", 44);
    public static final InputKey EQUAL = new InputKey(InputType.KEYBOARD, "key.keyboard.equal", 61);
    public static final InputKey TILDA = new InputKey(InputType.KEYBOARD, "key.keyboard.grave.accent", 96);
    public static final InputKey L_BRACKET = new InputKey(InputType.KEYBOARD, "key.keyboard.left.bracket", 91);
    public static final InputKey HYPHEN = new InputKey(InputType.KEYBOARD, "key.keyboard.minus", 45);
    public static final InputKey PERIOD = new InputKey(InputType.KEYBOARD, "key.keyboard.period", 46);
    public static final InputKey R_BRACKET = new InputKey(InputType.KEYBOARD, "key.keyboard.right.bracket", 93);
    public static final InputKey SEMICOLON = new InputKey(InputType.KEYBOARD, "key.keyboard.semicolon", 59);
    public static final InputKey SLASH = new InputKey(InputType.KEYBOARD, "key.keyboard.slash", 47);
    public static final InputKey SPACE = new InputKey(InputType.KEYBOARD, "key.keyboard.space", 32);
    public static final InputKey TAB = new InputKey(InputType.KEYBOARD, "key.keyboard.tab", 258);
    public static final InputKey L_ALT = new InputKey(InputType.KEYBOARD, "key.keyboard.left.alt", 342);
    public static final InputKey L_CTRL = new InputKey(InputType.KEYBOARD, "key.keyboard.left.control", 341);
    public static final InputKey L_SHIFT = new InputKey(InputType.KEYBOARD, "key.keyboard.left.shift", 340);
    public static final InputKey L_WIN = new InputKey(InputType.KEYBOARD, "key.keyboard.left.win", 343);
    public static final InputKey R_ALT = new InputKey(InputType.KEYBOARD, "key.keyboard.right.alt", 346);
    public static final InputKey R_CTRL = new InputKey(InputType.KEYBOARD, "key.keyboard.right.control", 345);
    public static final InputKey R_SHIFT = new InputKey(InputType.KEYBOARD, "key.keyboard.right.shift", 344);
    public static final InputKey R_WIN = new InputKey(InputType.KEYBOARD, "key.keyboard.right.win", 347);
    public static final InputKey ENTER = new InputKey(InputType.KEYBOARD, "key.keyboard.enter", 257);
    public static final InputKey ESC = new InputKey(InputType.KEYBOARD, "key.keyboard.escape", 256);
    public static final InputKey BACKSPACE = new InputKey(InputType.KEYBOARD, "key.keyboard.backspace", 259);
    public static final InputKey DEL = new InputKey(InputType.KEYBOARD, "key.keyboard.delete", 261);
    public static final InputKey END = new InputKey(InputType.KEYBOARD, "key.keyboard.end", 269);
    public static final InputKey HOME = new InputKey(InputType.KEYBOARD, "key.keyboard.home", 268);
    public static final InputKey INSERT = new InputKey(InputType.KEYBOARD, "key.keyboard.insert", 260);
    public static final InputKey PG_DOWN = new InputKey(InputType.KEYBOARD, "key.keyboard.page.down", 267);
    public static final InputKey PG_UP = new InputKey(InputType.KEYBOARD, "key.keyboard.page.up", 266);
    public static final InputKey CAPS_LOCK = new InputKey(InputType.KEYBOARD, "key.keyboard.caps.lock", 280);
    public static final InputKey PAUSE = new InputKey(InputType.KEYBOARD, "key.keyboard.pause", 284);
    public static final InputKey SCROLL_LOCK = new InputKey(InputType.KEYBOARD, "key.keyboard.scroll.lock", 281);
    public static final InputKey MENU = new InputKey(InputType.KEYBOARD, "key.keyboard.menu", 348);
    public static final InputKey PRINT_SCREEN = new InputKey(InputType.KEYBOARD, "key.keyboard.print.screen", 283);
    public static final InputKey WORLD_1 = new InputKey(InputType.KEYBOARD, "key.keyboard.world.1", 161);
    public static final InputKey WORLD_2 = new InputKey(InputType.KEYBOARD, "key.keyboard.world.2", 162);
    
    
    protected InputKey(InputType device, String name, int keyCode) {
    	this(device, name, keyCode, null);
    }
    
    protected InputKey(InputType device, String name, int keyCode, Modifier modifier) {
    	this.device = device;
    	this.name = name;
    	this.keyCode = keyCode;
    	this.modifier = modifier;
    }
    
    public InputKey withModifier(Modifier modifier) {
    	if (this.modifier != null) {
    		throw new IllegalStateException("This keybind already has a modifier, can't have two");
    	}
    	if (withModifiers == null) {
    		withModifiers = new EnumMap<>(Modifier.class);
    	}
    	return withModifiers.computeIfAbsent(modifier, m -> new InputKey(device, name + ":" + m.name(), keyCode, m));
    }
    
    public final InputType device;
    public final int keyCode;
    public final String name;
    @Nullable public final Modifier modifier;
    protected Map<Modifier, InputKey> withModifiers;
    
    public static enum InputType {
    	KEYBOARD,
    	MOUSE;
    }
    
    public static enum Modifier {
    	SHIFT,
    	CONTROL
    }
    
}
