package crucible.lens.ui.common

/**
 * The one legitimate alpha modifier in this app's color system. Every other "muted" look reaches
 * for a real M3 role (`onSurfaceVariant`, a `*Container` role) instead of fading one — those roles
 * already encode the right amount of de-emphasis with a guaranteed contrast ratio, so adding alpha
 * on top only weakens that guarantee for no reason.
 *
 * Disabled content is different in kind, not degree: WCAG 1.4.3 explicitly exempts inactive
 * components from the normal contrast requirement, because a disabled control isn't meant to be
 * read as active content. Material's own stock components implement that the same way everywhere —
 * as a fixed opacity reduction on top of whatever color the control would otherwise have, since
 * "disabled" is a state any element can enter, not a fixed color family of its own. [Disabled] is
 * exactly the value M3's own components use (confirmed against `FilledButtonTokens.kt`'s
 * `DisabledIconOpacity`/`DisabledLabelTextOpacity` in the resolved M3 1.4.0 sources) — use it with
 * `onSurfaceVariant` (M3's own disabled-content base role) rather than inventing another number.
 */
object AppContentAlpha {
    const val Disabled: Float = 0.38f
}
