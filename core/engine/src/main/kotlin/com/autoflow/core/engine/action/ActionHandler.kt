package com.autoflow.core.engine.action

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import kotlin.reflect.KClass

/**
 * Executes one kind of [ActionSpec]. Implementations live in platform modules
 * (Android APIs) or in the engine (pure logic like HTTP).
 * Handlers report problems through [ActionResult] instead of throwing whenever possible;
 * a thrown SecurityException is reported as a permission failure.
 */
fun interface ActionHandler<in A : ActionSpec> {
    suspend fun execute(action: A, context: AutomationContext): ActionResult
}

/** Maps action types to handlers. New actions are added by registering a handler; the engine is unchanged. */
class ActionRegistry private constructor(
    private val handlers: Map<KClass<out ActionSpec>, ActionHandler<*>>,
) {
    val supportedTypes: Set<KClass<out ActionSpec>> get() = handlers.keys

    @Suppress("UNCHECKED_CAST")
    fun <A : ActionSpec> handlerFor(action: A): ActionHandler<A>? = handlers[action::class] as ActionHandler<A>?

    class Builder {
        private val handlers = mutableMapOf<KClass<out ActionSpec>, ActionHandler<*>>()

        fun <A : ActionSpec> register(type: KClass<A>, handler: ActionHandler<A>): Builder = apply {
            handlers[type] = handler
        }

        inline fun <reified A : ActionSpec> register(handler: ActionHandler<A>): Builder = register(A::class, handler)

        fun build(): ActionRegistry = ActionRegistry(handlers.toMap())
    }
}
