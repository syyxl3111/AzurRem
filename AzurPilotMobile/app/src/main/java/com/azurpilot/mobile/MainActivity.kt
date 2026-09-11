package com.azurpilot.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azurpilot.mobile.ui.AppRoot
import com.azurpilot.mobile.ui.AppViewModel

class MainActivity : ComponentActivity() {

    private var vm: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val model: AppViewModel = viewModel()
            vm = model
            AppRoot(model)
        }
    }

    /**
     * 切到后台就停掉日志增量流。
     *
     * 它每秒打一次桥 —— 界面都看不见了还继续拉，纯属白耗电和网络。
     * 回到前台时如果还停在日志页，[AppViewModel.resumeForeground] 会把它接上，
     * 并且**从"最后 N 行"重新开始**（offset 归零），
     * 所以切后台期间漏掉的日志不会丢，也不会出现断层。
     */
    override fun onStop() {
        super.onStop()
        vm?.pauseForBackground()
    }

    override fun onStart() {
        super.onStart()
        vm?.resumeForeground()
    }
}
