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
        val actionStr = when (action) {
            ModuleAction.START -> "start"
            ModuleAction.STOP -> "stop"
            ModuleAction.CONFIGURE -> "configure"
        }
        return api.controlModule(moduleId, actionStr)
    }
}
