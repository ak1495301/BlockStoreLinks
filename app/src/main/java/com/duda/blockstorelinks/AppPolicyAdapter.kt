package com.duda.blockstorelinks

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class AppPolicyAdapter(
    private val context: Context,
    private val onSelectAction: (AppPolicy, LinkAction) -> Unit,
    private val onPickRedirect: (AppPolicy) -> Unit,
    private val onEditAllowlist: (AppPolicy) -> Unit,
    private val onEditRedirectRules: (AppPolicy) -> Unit,
    private val onResetAttempts: (AppPolicy) -> Unit,
    private val onShowHistory: (AppPolicy) -> Unit,
    private val resolveRedirectLabel: (AppPolicy) -> String
) : RecyclerView.Adapter<AppPolicyAdapter.PolicyViewHolder>() {

    private val items = mutableListOf<AppPolicy>()
    private val store = AppPolicyStore(context)

    fun submitList(policies: List<AppPolicy>) {
        val diffResult = DiffUtil.calculateDiff(AppPolicyDiff(items, policies))
        items.clear()
        items.addAll(policies)
        diffResult.dispatchUpdatesTo(this)
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
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val appPackage: TextView = itemView.findViewById(R.id.appPackage)
        private val attempts: TextView = itemView.findViewById(R.id.attempts)
        private val actionToggle: MaterialButtonToggleGroup = itemView.findViewById(R.id.actionToggle)
        private val actionAllow: MaterialButton = itemView.findViewById(R.id.actionAllow)
        private val actionAsk: MaterialButton = itemView.findViewById(R.id.actionAsk)
        private val actionDeny: MaterialButton = itemView.findViewById(R.id.actionDeny)
        private val redirectButton: Button = itemView.findViewById(R.id.redirectButton)
        private val redirectTarget: TextView = itemView.findViewById(R.id.redirectTarget)
        private val allowlistButton: Button = itemView.findViewById(R.id.allowlistButton)
        private val allowlistCount: TextView = itemView.findViewById(R.id.allowlistCount)
        private val redirectRulesButton: Button = itemView.findViewById(R.id.redirectRulesButton)
        private val redirectRulesCount: TextView = itemView.findViewById(R.id.redirectRulesCount)
        private val resetAttempts: Button = itemView.findViewById(R.id.resetAttempts)

        fun bind(policy: AppPolicy) {
            appIcon.setImageDrawable(resolveAppIcon(policy.packageName))
            appName.text = resolveAppLabel(policy.packageName)
            appPackage.text = policy.packageName
            attempts.text = context.getString(R.string.attempts_count, policy.attemptCount)

            actionToggle.removeOnButtonCheckedListener(actionListener)
            when (policy.linkAction) {
                LinkAction.ALLOW -> actionToggle.check(actionAllow.id)
                LinkAction.ASK -> actionToggle.check(actionAsk.id)
                LinkAction.DENY -> actionToggle.check(actionDeny.id)
            }
            actionToggle.addOnButtonCheckedListener(actionListener)

            redirectTarget.text = resolveRedirectLabel(policy)

            redirectButton.setOnClickListener {
                onPickRedirect(policy)
            }

            allowlistCount.text = context.getString(
                R.string.allowlist_count_value,
                policy.allowlistPatterns.size
            )
            allowlistButton.setOnClickListener {
                onEditAllowlist(policy)
            }

            redirectRulesCount.text = context.getString(
                R.string.redirect_rules_count_value,
                policy.redirectRules.size
            )
            redirectRulesButton.setOnClickListener {
                onEditRedirectRules(policy)
            }
            resetAttempts.setOnClickListener {
                onResetAttempts(policy)
            }
            itemView.setOnClickListener {
                onShowHistory(policy)
            }
        }

        private val actionListener =
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                val action = when (checkedId) {
                    actionAllow.id -> LinkAction.ALLOW
                    actionAsk.id -> LinkAction.ASK
                    actionDeny.id -> LinkAction.DENY
                    else -> LinkAction.ALLOW
                }
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@OnButtonCheckedListener
                val policy = items[position]
                onSelectAction(policy, action)
            }

        private fun resolveAppLabel(packageName: String): String {
            store.getLabelOverride(packageName)?.let { return it }
            if (packageName == UNKNOWN_PACKAGE) {
                return context.getString(R.string.unknown_app_no_referrer)
            }
            return try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (ex: PackageManager.NameNotFoundException) {
                packageName
            }
        }

        private fun resolveAppIcon(packageName: String) =
            try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                info.loadIcon(context.packageManager)
            } catch (ex: Exception) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)
            }
    }

    private class AppPolicyDiff(
        private val oldItems: List<AppPolicy>,
        private val newItems: List<AppPolicy>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].packageName == newItems[newItemPosition].packageName
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }

    companion object {
        private const val UNKNOWN_PACKAGE = "unknown"
    }
}
