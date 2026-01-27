package com.goldenmoonsolutions.myshoppinglist.viewmodel

import androidx.lifecycle.ViewModel
import com.goldenmoonsolutions.myshoppinglist.domain.ShoppingItem

// ViewModel is kept in memory by the Android OS until the screen is permanently closed.
class ShoppingListViewModel : ViewModel() {
    private val _items = mutableListOf<ShoppingItem>()
    val items: List<ShoppingItem> get() = _items

    fun addItem(name: String, quantity: Int) {
        val newId = if (_items.isEmpty()) 1 else _items.maxOf { it.id } + 1
        _items.add(ShoppingItem(id = newId, name = name, quantity = quantity))
    }

    fun removeItem(item: ShoppingItem) {
        _items.remove(item)
    }

    fun toggleItem(item: ShoppingItem, isChecked: Boolean) {
        // In Compose, to trigger a redraw for a specific item property change,
        // we find the index and update the element.
        val index = _items.indexOf(item)
        if (index != -1) {
            _items[index] = _items[index].copy(isChecked = isChecked)
        }
    }
}