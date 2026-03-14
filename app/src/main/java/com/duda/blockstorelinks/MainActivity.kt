package com.duda.blockstorelinks

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    private lateinit var store: AppPolicyStore
    private lateinit var adapter: AppPolicyAdapter
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = AppPolicyStore(this)
        emptyState = findViewById(R.id.emptyState)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val recyclerView = findViewById<RecyclerView>(R.id.appList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppPolicyAdapter(
            context = this,
            onToggleBlocked = { policy, blocked ->
                store.setBlocked(policy.packageName, blocked)
                refreshList()
            },
            onPickRedirect = { policy ->
                showRedirectPicker(policy)
            }
        )
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            refreshList()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val policies = store.getPolicies()
        adapter.submitList(policies)
        emptyState.visibility = if (policies.isEmpty()) View.VISIBLE else View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showRedirectPicker(policy: AppPolicy) {
        val browsers = getBrowserTargets()
        val labels = mutableListOf(getString(R.string.clear_redirect))
        val packages = mutableListOf<String?>(null)

        browsers.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pick_browser))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                store.setRedirect(policy.packageName, selectedPackage)
                refreshList()
            }
            .show()
    }

    private fun getBrowserTargets(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolved = packageManager.queryIntentActivities(intent, 0)
        return resolved.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }
}
