package ru.hdlabs.treex.ui.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.hdlabs.treex.di.AppContainer

class TreeViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TreeViewModel::class.java)) {
            return TreeViewModel(container.treeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
