/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.avatok.comms.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.avatok.comms.account.AccountEditionFragment
import com.avatok.comms.databinding.FragExtensionHandlersListBinding
import com.avatok.comms.extensions.ExtensionUtils
import com.avatok.comms.settings.extensionssettings.ExtensionDetails
import com.avatok.comms.settings.extensionssettings.ExtensionsListAdapter
import com.avatok.comms.settings.extensionssettings.ExtensionsListAdapter.ExtensionListItemListener
import com.avatok.comms.utils.ConversationPath
import net.jami.daemon.JamiService

class ExtensionHandlersListFragment : Fragment(), ExtensionListItemListener {
    private var binding: FragExtensionHandlersListBinding? = null
    private lateinit var mPath: ConversationPath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPath = ConversationPath.fromBundle(requireArguments())!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragExtensionHandlersListBinding.inflate(inflater, container, false).also { b ->
            b.handlerList.setHasFixedSize(true)
            b.handlerList.adapter = ExtensionsListAdapter(
                ExtensionUtils.getChatHandlersDetails(b.handlerList.context, mPath.accountId, mPath.conversationId.removePrefix("swarm:")), this, "")
            binding = b
        }.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding!!.chatExtensionsToolbar.visibility = View.VISIBLE
        binding!!.chatExtensionsToolbar.setOnClickListener { v: View? ->
            val fragment = parentFragment
            if (fragment is ConversationFragment) {
                fragment.hideExtensionListHandlers()
            }
        }
    }

    override fun onExtensionItemClicked(extensionDetails: ExtensionDetails) {
        JamiService.toggleChatHandler(extensionDetails.handlerId, mPath.accountId, mPath.conversationId.removePrefix("swarm:"), extensionDetails.isEnabled)
    }

    override fun onExtensionEnabled(extensionDetails: ExtensionDetails) {
        JamiService.toggleChatHandler(extensionDetails.handlerId, mPath.accountId, mPath.conversationId.removePrefix("swarm:"), extensionDetails.isEnabled)
    }

    companion object {
        val TAG = ExtensionHandlersListFragment::class.simpleName!!
        fun newInstance(accountId: String, peerId: String): ExtensionHandlersListFragment {
            val fragment = ExtensionHandlersListFragment()
            fragment.arguments = ConversationPath.toBundle(accountId, peerId)
            return fragment
        }
    }
}