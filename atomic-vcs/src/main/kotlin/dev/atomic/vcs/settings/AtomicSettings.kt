package dev.atomic.vcs.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "dev.atomic.vcs.settings.AtomicSettings",
    storages = [Storage("atomic.xml")],
)
class AtomicSettings(val project: Project) : PersistentStateComponent<AtomicSettings.State> {
    data class State(
        var executablePath: String = "",
        var autoRegisterMcp: Boolean = true,
        var enableAgentTracking: Boolean = true,
        var defaultParentView: String = "main",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var executablePath: String
        get() = myState.executablePath
        set(value) {
            myState.executablePath = value
        }

    var autoRegisterMcp: Boolean
        get() = myState.autoRegisterMcp
        set(value) {
            myState.autoRegisterMcp = value
        }

    var enableAgentTracking: Boolean
        get() = myState.enableAgentTracking
        set(value) {
            myState.enableAgentTracking = value
        }

    var defaultParentView: String
        get() = myState.defaultParentView
        set(value) {
            myState.defaultParentView = value
        }

    companion object {
        fun getInstance(project: Project): AtomicSettings = project.getService(AtomicSettings::class.java)
    }
}
