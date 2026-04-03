package com.cloudfin.data.repository

import com.cloudfin.data.api.CloudfinApi
import com.cloudfin.model.CoreStatus
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleAction

class CloudfinRepository(
    private val api: CloudfinApi = CloudfinApi()
) {
    suspend fun getCoreStatus(): Result<CoreStatus> = api.getCoreStatus()

    suspend fun getModules(): Result<List<ModuleInfo>> = api.getModules()

    suspend fun controlModule(moduleId: String, action: ModuleAction): Result<Unit> {
        return when (action) {
            ModuleAction.START -> api.loadModule(moduleId)
            ModuleAction.STOP -> api.unloadModule(moduleId)
            ModuleAction.CONFIGURE -> api.loadModule(moduleId) // configure uses load as approximation
        }
    }
}
