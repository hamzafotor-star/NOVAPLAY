package tv.novaplay.iptv

import android.app.Application
import tv.novaplay.iptv.data.local.IptvDatabase
import tv.novaplay.iptv.data.repository.IptvRepository
import tv.novaplay.iptv.player.HybridPlayerController

class NovaPlayApplication : Application() {

    lateinit var database: IptvDatabase
        private set

    lateinit var repository: IptvRepository
        private set

    lateinit var playerController: HybridPlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        database = IptvDatabase(this)
        repository = IptvRepository(database)
        playerController = HybridPlayerController(this)
    }

    override fun onTerminate() {
        playerController.release()
        super.onTerminate()
    }
}

