package com.hezb.hplayer.ui.widget

import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.hezb.hplayer.R

class ExpandedControlsActivity: ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }
}