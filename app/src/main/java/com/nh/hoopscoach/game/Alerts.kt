package com.nh.hoopscoach.game

/** Pop-ups for the user's big moments. They queue (a long sim can bring several) and show one at a time. */
object Alerts {
    fun add(lg: League, title: String, body: String, kind: String = "") {
        lg.alerts += Alert(title, body, kind)
        if (lg.alerts.size > 8) lg.alerts.removeAt(0)
    }
}
