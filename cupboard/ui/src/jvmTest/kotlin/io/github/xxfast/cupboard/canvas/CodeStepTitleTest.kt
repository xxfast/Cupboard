package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.ActionKind
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAction
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.action
import io.github.xxfast.cupboard.document.stepBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which builds the inspector offers Magic Move or Typewriter on, and what it calls them. */
class CodeStepTitleTest {
    private val frame = Frame(0f, 0f, 100f, 100f)
    private val code = CodeElement(id = "code", frame = frame)
    private val diagram = DiagramElement(id = "diagram", frame = frame)

    @Test
    fun aCodeBlocksStepBuildIsACodeStep() {
        assertTrue(stepBuild(code.id, 1).isCodeStep(code))
    }

    @Test
    fun aDiagramsStepBuildIsNot() {
        assertFalse(stepBuild(diagram.id, 1).isCodeStep(diagram))
    }

    @Test
    fun anActionThatDoesSomethingIsNot() {
        val moving: Build = stepBuild(code.id, 1).copy(action = BuildAction(ActionKind.Move))
        assertFalse(moving.isCodeStep(code))
        assertFalse(Build.action(code.id, BuildAction(ActionKind.Scale)).isCodeStep(code))
    }

    @Test
    fun aBuildInIsNot() {
        assertFalse(stepBuild(code.id, 1).copy(kind = BuildKind.In).isCodeStep(code))
    }

    @Test
    fun everyEffectButTypewriterReadsMagicMove() {
        BuildEffect.entries.forEach { effect ->
            val expected: String = if (effect == BuildEffect.Typewriter) "Typewriter" else "Magic Move"
            assertEquals(expected, effect.codeStepTitle())
        }
    }
}
