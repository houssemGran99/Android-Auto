package com.autoflow.app.di

import android.content.Context
import com.autoflow.app.runtime.AndroidEngineLogger
import com.autoflow.app.runtime.AppForegroundTracker
import com.autoflow.app.runtime.TimeTriggerReceiver
import com.autoflow.core.engine.AutomationEngine
import com.autoflow.core.engine.DeviceStateProvider
import com.autoflow.core.engine.EngineLogger
import com.autoflow.core.engine.action.ActionPipeline
import com.autoflow.core.engine.action.ActionRegistry
import com.autoflow.core.engine.condition.ConditionEvaluator
import com.autoflow.core.engine.repository.AutomationRepository
import com.autoflow.core.engine.repository.EngineSettings
import com.autoflow.core.engine.repository.ExecutionRepository
import com.autoflow.core.engine.repository.VariableRepository
import com.autoflow.data.storage.AutoFlowDatabase
import com.autoflow.data.storage.repository.RoomAutomationRepository
import com.autoflow.data.storage.repository.RoomExecutionRepository
import com.autoflow.data.storage.repository.RoomVariableRepository
import com.autoflow.data.storage.security.KeystoreSecretCipher
import com.autoflow.data.storage.settings.SettingsRepository
import com.autoflow.platform.actions.OkHttpExecutor
import com.autoflow.platform.actions.PlatformActions
import com.autoflow.platform.permissions.PermissionManager
import com.autoflow.platform.triggers.AndroidDeviceStateProvider
import com.autoflow.platform.triggers.TimeTriggerScheduler
import com.autoflow.platform.triggers.TriggerSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive screens (engine runs, coordinator). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AutoFlowDatabase = AutoFlowDatabase.create(context)

    @Provides
    @Singleton
    fun automationRepository(db: AutoFlowDatabase): AutomationRepository = RoomAutomationRepository(db.automationDao())

    @Provides
    @Singleton
    fun executionRepository(db: AutoFlowDatabase): ExecutionRepository = RoomExecutionRepository(db.executionDao())

    @Provides
    @Singleton
    fun variableRepository(db: AutoFlowDatabase): VariableRepository =
        RoomVariableRepository(db.variableDao(), KeystoreSecretCipher())

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository = SettingsRepository.create(context)

    @Provides
    fun engineSettings(settings: SettingsRepository): EngineSettings = settings

    @Provides
    @Singleton
    fun permissionManager(@ApplicationContext context: Context): PermissionManager = PermissionManager(context)

    @Provides
    @Singleton
    fun deviceStateProvider(
        @ApplicationContext context: Context,
        permissions: PermissionManager,
    ): DeviceStateProvider = AndroidDeviceStateProvider(context, permissions)

    @Provides
    @Singleton
    fun engineLogger(): EngineLogger = AndroidEngineLogger

    @Provides
    @Singleton
    fun actionRegistry(
        @ApplicationContext context: Context,
        permissions: PermissionManager,
        foregroundTracker: AppForegroundTracker,
    ): ActionRegistry = PlatformActions.registry(context, permissions, OkHttpExecutor(), foregroundTracker::isInForeground)

    @Provides
    @Singleton
    fun conditionEvaluator(): ConditionEvaluator = ConditionEvaluator()

    @Provides
    @Singleton
    fun actionPipeline(
        registry: ActionRegistry,
        conditionEvaluator: ConditionEvaluator,
        logger: EngineLogger,
    ): ActionPipeline = ActionPipeline(registry, conditionEvaluator, logger)

    @Provides
    @Singleton
    fun automationEngine(
        automations: AutomationRepository,
        executions: ExecutionRepository,
        variables: VariableRepository,
        settings: EngineSettings,
        deviceStateProvider: DeviceStateProvider,
        pipeline: ActionPipeline,
        conditionEvaluator: ConditionEvaluator,
        logger: EngineLogger,
    ): AutomationEngine = AutomationEngine(
        automations = automations,
        executions = executions,
        variables = variables,
        settings = settings,
        deviceStateProvider = deviceStateProvider,
        pipeline = pipeline,
        conditionEvaluator = conditionEvaluator,
        logger = logger,
    )

    @Provides
    @Singleton
    fun timeTriggerScheduler(@ApplicationContext context: Context): TimeTriggerScheduler =
        TimeTriggerScheduler(context, TimeTriggerReceiver::class.java)

    @Provides
    @Singleton
    fun triggerSourceFactory(
        @ApplicationContext context: Context,
        permissions: PermissionManager,
    ): TriggerSourceFactory = TriggerSourceFactory(context, permissions)
}
