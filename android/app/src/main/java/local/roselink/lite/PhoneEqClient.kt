package local.roselink.lite

import android.content.*
import android.os.IBinder

internal class PhoneEqClient(context: Context, private val listener: (PhoneEqState) -> Unit) {
    private val context = context.applicationContext
    private var service: PhoneEqService? = null
    private var closed = false
    private val pending = linkedMapOf<Int, String>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            service = (binder as PhoneEqService.LocalBinder).service
            service!!.addListener(listener)
            pending.forEach { (id, owner) -> service!!.offerSession(id, owner) }; pending.clear()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            if (!closed) listener(PhoneEqState().apply { status = "手机 EQ 服务已停止，请重新启用" })
        }
    }
    private val bound = this.context.bindService(Intent(this.context, PhoneEqService::class.java), connection, Context.BIND_AUTO_CREATE)
    fun enable(gains: IntArray, global: Boolean) {
        if (closed) return
        try { context.startForegroundService(Intent(context, PhoneEqService::class.java).setAction(PhoneEqService.ENABLE)
            .putExtra(PhoneEqService.GAINS, gains.clone()).putExtra(PhoneEqService.GLOBAL, global)) }
        catch (error: Exception) { listener(PhoneEqState().apply { status = "启动手机EQ失败：${error.message}" }) }
    }
    fun disable() { service?.disable() }
    fun retry() { service?.retry() }
    fun offerSession(id: Int, owner: String) {
        if (closed || id <= 0) return
        if (service != null) service!!.offerSession(id, owner)
        else if (pending.size < 16) pending[id] = owner.take(120)
    }
    fun close() {
        closed = true; service?.removeListener(listener); service = null; pending.clear()
        if (bound) context.unbindService(connection)
    }
}
