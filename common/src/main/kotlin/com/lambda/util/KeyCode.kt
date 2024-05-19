package com.lambda.util

import com.lambda.util.primitives.extension.displayValue
import org.lwjgl.glfw.GLFW
import java.util.*

const val charNames = "`-=[]\\,;\'./"
val charKeys = intArrayOf(
    GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
    GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_BACKSLASH,
    GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
    GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH, 0
)

enum class KeyCode(val keyCode: Int) {
    Unbound(GLFW.GLFW_KEY_UNKNOWN),
    Space(GLFW.GLFW_KEY_SPACE),
    Apostrophe(GLFW.GLFW_KEY_APOSTROPHE),
    Comma(GLFW.GLFW_KEY_COMMA),
    Minus(GLFW.GLFW_KEY_MINUS),
    Period(GLFW.GLFW_KEY_PERIOD),
    Slash(GLFW.GLFW_KEY_SLASH),
    Num0(GLFW.GLFW_KEY_0),
    Num1(GLFW.GLFW_KEY_1),
    Num2(GLFW.GLFW_KEY_2),
    Num3(GLFW.GLFW_KEY_3),
    Num4(GLFW.GLFW_KEY_4),
    Num5(GLFW.GLFW_KEY_5),
    Num6(GLFW.GLFW_KEY_6),
    Num7(GLFW.GLFW_KEY_7),
    Num8(GLFW.GLFW_KEY_8),
    Num9(GLFW.GLFW_KEY_9),
    Semicolon(GLFW.GLFW_KEY_SEMICOLON),
    Equal(GLFW.GLFW_KEY_EQUAL),
    A(GLFW.GLFW_KEY_A),
    B(GLFW.GLFW_KEY_B),
    C(GLFW.GLFW_KEY_C),
    D(GLFW.GLFW_KEY_D),
    E(GLFW.GLFW_KEY_E),
    F(GLFW.GLFW_KEY_F),
    G(GLFW.GLFW_KEY_G),
    H(GLFW.GLFW_KEY_H),
    I(GLFW.GLFW_KEY_I),
    J(GLFW.GLFW_KEY_J),
    K(GLFW.GLFW_KEY_K),
    L(GLFW.GLFW_KEY_L),
    M(GLFW.GLFW_KEY_M),
    N(GLFW.GLFW_KEY_N),
    O(GLFW.GLFW_KEY_O),
    P(GLFW.GLFW_KEY_P),
    Q(GLFW.GLFW_KEY_Q),
    R(GLFW.GLFW_KEY_R),
    S(GLFW.GLFW_KEY_S),
    T(GLFW.GLFW_KEY_T),
    U(GLFW.GLFW_KEY_U),
    V(GLFW.GLFW_KEY_V),
    W(GLFW.GLFW_KEY_W),
    X(GLFW.GLFW_KEY_X),
    Y(GLFW.GLFW_KEY_Y),
    Z(GLFW.GLFW_KEY_Z),
    LeftBracket(GLFW.GLFW_KEY_LEFT_BRACKET),
    Backslash(GLFW.GLFW_KEY_BACKSLASH),
    RightBracket(GLFW.GLFW_KEY_RIGHT_BRACKET),
    GraveAccent(GLFW.GLFW_KEY_GRAVE_ACCENT),
    World1(GLFW.GLFW_KEY_WORLD_1),
    World2(GLFW.GLFW_KEY_WORLD_2),
    Escape(GLFW.GLFW_KEY_ESCAPE),
    Enter(GLFW.GLFW_KEY_ENTER),
    Tab(GLFW.GLFW_KEY_TAB),
    Backspace(GLFW.GLFW_KEY_BACKSPACE),
    Insert(GLFW.GLFW_KEY_INSERT),
    Delete(GLFW.GLFW_KEY_DELETE),
    Right(GLFW.GLFW_KEY_RIGHT),
    Left(GLFW.GLFW_KEY_LEFT),
    Down(GLFW.GLFW_KEY_DOWN),
    Up(GLFW.GLFW_KEY_UP),
    PageUp(GLFW.GLFW_KEY_PAGE_UP),
    PageDown(GLFW.GLFW_KEY_PAGE_DOWN),
    Home(GLFW.GLFW_KEY_HOME),
    End(GLFW.GLFW_KEY_END),
    CapsLock(GLFW.GLFW_KEY_CAPS_LOCK),
    ScrollLock(GLFW.GLFW_KEY_SCROLL_LOCK),
    NumLock(GLFW.GLFW_KEY_NUM_LOCK),
    PrintScreen(GLFW.GLFW_KEY_PRINT_SCREEN),
    Pause(GLFW.GLFW_KEY_PAUSE),
    F1(GLFW.GLFW_KEY_F1),
    F2(GLFW.GLFW_KEY_F2),
    F3(GLFW.GLFW_KEY_F3),
    F4(GLFW.GLFW_KEY_F4),
    F5(GLFW.GLFW_KEY_F5),
    F6(GLFW.GLFW_KEY_F6),
    F7(GLFW.GLFW_KEY_F7),
    F8(GLFW.GLFW_KEY_F8),
    F9(GLFW.GLFW_KEY_F9),
    F10(GLFW.GLFW_KEY_F10),
    F11(GLFW.GLFW_KEY_F11),
    F12(GLFW.GLFW_KEY_F12),
    F13(GLFW.GLFW_KEY_F13),
    F14(GLFW.GLFW_KEY_F14),
    F15(GLFW.GLFW_KEY_F15),
    F16(GLFW.GLFW_KEY_F16),
    F17(GLFW.GLFW_KEY_F17),
    F18(GLFW.GLFW_KEY_F18),
    F19(GLFW.GLFW_KEY_F19),
    F20(GLFW.GLFW_KEY_F20),
    F21(GLFW.GLFW_KEY_F21),
    F22(GLFW.GLFW_KEY_F22),
    F23(GLFW.GLFW_KEY_F23),
    F24(GLFW.GLFW_KEY_F24),
    F25(GLFW.GLFW_KEY_F25),
    KP_0(GLFW.GLFW_KEY_KP_0),
    KP_1(GLFW.GLFW_KEY_KP_1),
    KP_2(GLFW.GLFW_KEY_KP_2),
    KP_3(GLFW.GLFW_KEY_KP_3),
    KP_4(GLFW.GLFW_KEY_KP_4),
    KP_5(GLFW.GLFW_KEY_KP_5),
    KP_6(GLFW.GLFW_KEY_KP_6),
    KP_7(GLFW.GLFW_KEY_KP_7),
    KP_8(GLFW.GLFW_KEY_KP_8),
    KP_9(GLFW.GLFW_KEY_KP_9),
    KP_DECIMAL(GLFW.GLFW_KEY_KP_DECIMAL),
    KP_DIVIDE(GLFW.GLFW_KEY_KP_DIVIDE),
    KP_MULTIPLY(GLFW.GLFW_KEY_KP_MULTIPLY),
    KP_SUBTRACT(GLFW.GLFW_KEY_KP_SUBTRACT),
    KP_ADD(GLFW.GLFW_KEY_KP_ADD),
    KP_ENTER(GLFW.GLFW_KEY_KP_ENTER),
    KP_EQUAL(GLFW.GLFW_KEY_KP_EQUAL),
    LEFT_SHIFT(GLFW.GLFW_KEY_LEFT_SHIFT),
    LEFT_CONTROL(GLFW.GLFW_KEY_LEFT_CONTROL),
    LEFT_ALT(GLFW.GLFW_KEY_LEFT_ALT),
    LEFT_SUPER(GLFW.GLFW_KEY_LEFT_SUPER),
    RIGHT_SHIFT(GLFW.GLFW_KEY_RIGHT_SHIFT),
    RIGHT_CONTROL(GLFW.GLFW_KEY_RIGHT_CONTROL),
    RIGHT_ALT(GLFW.GLFW_KEY_RIGHT_ALT),
    RIGHT_SUPER(GLFW.GLFW_KEY_RIGHT_SUPER),
    MENU(GLFW.GLFW_KEY_MENU),
    LAST(GLFW.GLFW_KEY_LAST);

    val localizedName by lazy { GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: displayValue }

    companion object {
        private val keyCodeMap: Map<Int, KeyCode> = entries.associateBy { it.keyCode }
        private val nameMap: Map<String, KeyCode> = entries.associateBy { it.name.lowercase(Locale.getDefault()) }

        fun fromKeyCodeOrNull(keyCode: Int) = keyCodeMap[keyCode]
        fun fromKeyCode(keyCode: Int) = fromKeyCodeOrNull(keyCode) ?: Unbound

        fun fromNameOrNull(name: String) = nameMap[name.lowercase(Locale.getDefault())]
        fun fromName(name: String) = fromNameOrNull(name) ?: Unbound

        fun translateKeyCode(key: Int, scanCode: Int): KeyCode {
            if (key in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_EQUAL) {
                return fromKeyCode(key)
            }

            val keyName = GLFW.glfwGetKeyName(key, scanCode) ?: return fromKeyCode(key)

            if (keyName.length == 1) {
                when (val char = keyName[0]) {
                    in '0'..'9' -> return fromKeyCode(GLFW.GLFW_KEY_0 + (char - '0'))
                    in 'A'..'Z' -> return fromKeyCode(GLFW.GLFW_KEY_A + (char - 'A'))
                    in 'a'..'z' -> return fromKeyCode(GLFW.GLFW_KEY_A + (char - 'a'))
                    else -> {
                        val index = charNames.indexOf(char)
                        if (index != -1) {
                            return fromKeyCode(charKeys[index])
                        }
                    }
                }
            }

            return fromKeyCode(key)
        }
    }
}
