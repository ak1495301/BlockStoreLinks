package com.duda.blockstorelinks

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class AppPolicyAdapter(
    private val context: Context,
    private val onToggleBlocked: (AppPolicy, Boolean) -> Unit,
    private val onPickRedirect: (AppPolicy) -> Unit
) : RecyclerView.Adapter<AppPolicyAdapter.PolicyViewHolder>() {

    private val items = mutableListOf<AppPolicy>()

    fun submitList(policies: List<AppPolicy>) {
        items.clear()
        items.addAll(policies)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PolicyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_policy, parent, false)
        return PolicyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PolicyViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PolicyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val appPackage: TextView = itemView.findViewById(R.id.appPackage)
        private val attempts: TextView = itemView.findViewById(R.id.attempts)
        private val blockedSwitch: SwitchMaterial = itemView.findViewById(R.id.blockedSwitch)
        private val redirectButton: Button = itemView.findViewById(R.id.redirectButton)
        private val redirectTarget: TextView = itemView.findViewById(R.id.redirectTarget)

        fun bind(policy: AppPolicy) {
            appName.text = resolveAppLabel(policy.packageName)
            appPackage.text = policy.packageName
            attempts.text = context.getString(R.string.attempts) + ": " + policy.attemptCount

            blockedSwitch.setOnCheckedChangeListener(null)
            blockedSwitch.isChecked = policy.blocked
            blockedSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleBlocked(policy, isChecked)
            }

            val redirectLabel = policy.redirectPackage?.let { resolveAppLabel(it) }
                ?: context.getString(R.string.clear_redirect)
            redirectTarget.text = redirectLabel

            redirectButton.setOnClickListener {
                onPickRedirect(policy)
            }
        }

        private fun resolveAppLabel(packageName: String): String {
            return try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (ex: PackageManager.NameNotFoundException) {
                context.getString(R.string.unknown_app)
            }
        }
    }
}
