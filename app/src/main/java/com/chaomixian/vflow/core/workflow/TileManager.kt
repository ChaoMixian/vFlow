package com.chaomixian.vflow.core.workflow

import android.content.Context
import com.chaomixian.vflow.core.workflow.model.WorkflowTile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TileManager(val context: Context) {
    private val prefs = context.getSharedPreferences("vflow_tiles", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_TILES = "tile_list"
    }

    /**
     * 保存Tile分配
     */
    fun saveTile(tile: WorkflowTile) {
        val tiles = getAllTiles().toMutableList()
        val index = tiles.indexOfFirst { it.tileIndex == tile.tileIndex }
        if (index != -1) {
            tiles[index] = tile
        } else {
            tiles.add(tile)
        }
        saveAllTiles(tiles)
    }

    /**
     * 获取指定索引的Tile
     */
    fun getTile(tileIndex: Int): WorkflowTile? {
        return getAllTiles().find { it.tileIndex == tileIndex }
    }

    /**
     * 获取所有已分配的Tile
     */
    fun getAllTiles(): List<WorkflowTile> {
        val json = prefs.getString(KEY_TILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WorkflowTile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有20个Tile的列表（包括未分配的）
     */
    fun getAllTilesWithEmpty(): List<WorkflowTile> {
        val assignedTiles = getAllTiles()
        return (0 until WorkflowTile.TILE_COUNT).map { index ->
            assignedTiles.find { it.tileIndex == index } ?: WorkflowTile(index, null)
        }
    }

    /**
     * 移除Tile分配
     */
    fun removeTile(tileIndex: Int) {
        val tiles = getAllTiles().toMutableList()
        tiles.removeAll { it.tileIndex == tileIndex }
        saveAllTiles(tiles)
    }

    /**
     * 根据workflowId获取已分配的Tile
     */
    fun getTileByWorkflowId(workflowId: String): WorkflowTile? {
        return getAllTiles().find { it.workflowId == workflowId }
    }

    /**
     * 移除工作流的Tile分配
     */
    fun removeTileByWorkflowId(workflowId: String) {
        val tiles = getAllTiles().toMutableList()
        tiles.removeAll { it.workflowId == workflowId }
        saveAllTiles(tiles)
    }

    /**
     * 批量保存所有Tile
     */
    private fun saveAllTiles(tiles: List<WorkflowTile>) {
        val json = gson.toJson(tiles)
        prefs.edit().putString(KEY_TILES, json).apply()
    }
}
