/*
 * Copyright (C) 2013 Piotr Wójcik
 * 
 * This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Very important!
 * 
 * Block ID and metadata are of type BYTE (8 bit). Do not use INT
 */
package net.comcraft.src;

import java.util.Vector;
import net.comcraft.client.Comcraft;

public final class World {

    public int worldSize;
    public final Comcraft cc;
    private WorldSaveType worldSaveType;
    public ChunkManager chunkProvider;
    private SaveInfo saveInfo;
    private WorldInfo worldInfo;
    private long lastAutosaveTime;
    private WorldUpdater worldUpdater;
    public long startTime;

    public World(Comcraft cc, SaveInfo saveHandler, WorldSaveType worldSaveType) {
        this.cc = cc;
        this.worldSaveType = worldSaveType;
        this.saveInfo = saveHandler;
        worldInfo = saveHandler.loadWorldInfo(cc.player);

        worldSize = worldInfo.getWorldSize();

        chunkProvider = createChunkProvider();
        worldUpdater = new WorldUpdater(this);
        startTime = System.currentTimeMillis();
    }

    public WorldUpdater getWorldUpdater() {
        return worldUpdater;
    }

    public void checkAutosave() {
        if (lastAutosaveTime == 0) {
            lastAutosaveTime = cc.currentTime;
        }

        if (cc.currentTime - lastAutosaveTime > cc.settings.autosaveTime) {
            saveWorld(null);
            lastAutosaveTime = cc.currentTime;
        }
    }

    public void loadAllChunks() {
        for (int z = 0; z < worldSize; ++z) {
            for (int x = 0; x < worldSize; ++x) {
                getChunkFromChunkCoords(x, z);
            }
        }

        cc.displayGuiScreen(new GuiLoadingWorld());
    }

    public void updateCurrentChunksList(Vector currentChunksList) {
        currentChunksList.removeAllElements();

        int renderDistance = cc.settings.renderDistance;

        for (int z = -renderDistance; z <= renderDistance; ++z) {
            for (int x = -renderDistance; x <= renderDistance; ++x) {
                Chunk chunk = cc.world.getChunkFromBlockCoords(((int) cc.player.xPos) + (x << 2), ((int) cc.player.zPos) + (z << 2));
                currentChunksList.addElement(chunk);
            }
        }
    }

    public void saveWorld(LoadingScreen loadingScreen) {
        if (loadingScreen != null) {
            loadingScreen.displayLoadingScreen(cc.langBundle.getText("GuiLoading.savingWorldText"));
        }

        saveInfo.saveWorldInfo(worldInfo, cc.player);
        chunkProvider.saveAllChunks(loadingScreen);
    }

    public ChunkManager createChunkProvider() {
        ChunkLoader chunkLoader = saveInfo.getChunkLoader(this);
        return new ChunkManager(cc, chunkLoader, this);
    }

    public void onWorldEnd() {
        chunkProvider.onChunkProviderEnd();
    }

    public AxisAlignedBB getBlockBoundingBox(int x, int y, int z) {
        int id = getBlockID(x, y, z);

        if (Block.blocksList[id] == null) {
            return null;
        }

        return Block.blocksList[id].getCollisionBoundingBoxFromPool(this, x, y, z);
    }

    public boolean isAirBlock(int x, int y, int z) {
        return getBlockID(x, y, z) == 0;
    }

    public Chunk getChunkFromBlockCoords(int x, int z) {
        return getChunkFromChunkCoords(x >> 2, z >> 2);
    }

    public Chunk getChunkFromChunkCoords(int x, int z) {
        return chunkProvider.getChunk(x, z);
    }

    public Block getBlock(int x, int y, int z) {
        return Block.blocksList[getBlockID(x, y, z)];
    }

    public int getBlockID(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= worldSize << 2 || z >= worldSize << 2) {
            return 1;
        } else if (y < 0 || y >= 32) {
            return 0;
        } else {
            return getChunkFromChunkCoords(x >> 2, z >> 2).getBlockID(x & 3, y, z & 3);
        }
    }

    public int getBlockMetadata(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= worldSize << 2 || z >= worldSize << 2) {
            return 0;
        } else if (y < 0 || y >= 32) {
            return 0;
        } else {
            return getChunkFromChunkCoords(x >> 2, z >> 2).getBlockMetadata(x & 3, y, z & 3);
        }
    }

    public boolean setBlockAndMetadata(int x, int y, int z, int id, int metadata) {
        if (x < 0 || z < 0 || x >= worldSize << 2 || z >= worldSize << 2 || y < 0 || y >= 32) {
            return false;
        } else {
            Chunk chunk = getChunkFromChunkCoords(x >> 2, z >> 2);
            return chunk.setBlockIDWithMetadata(x & 3, y, z & 3, id, metadata);
        }
    }

    public boolean setBlockID(int x, int y, int z, int id) {
        if (x < 0 || z < 0 || x >= worldSize << 2 || z >= worldSize << 2 || y < 0 || y >= 32) {
            return false;
        } else {
            Chunk chunk = getChunkFromChunkCoords(x >> 2, z >> 2);
            return chunk.setBlockID(x & 3, y, z & 3, id);
        }
    }

    /*
     * Rarely used function
     */
    public boolean setBlockN(int x, int y, int z, int id) {
        if (setBlockID(x, y, z, id)) {
            notifyBlocks(x, y, z, id);
            return true;
        } else {
            return false;
        }
    }

    /*
     * Rarely used function
     */
    public boolean setBlockAndMetadataN(int x, int y, int z, int id, int metadata) {
        if (setBlockAndMetadata(x, y, z, id, metadata)) {
            notifyBlocks(x, y, z, id);
            return true;
        } else {
            return false;
        }
    }

    /*
     * Very important!
     * 
     * Block ID and metadata are of type BYTE (8 bit). Do not use INT
     */
    public boolean setBlockMetadata(int x, int y, int z, int metadata) {
        if (x < 0 || z < 0 || x >= worldSize << 2 || z >= worldSize << 2 || y < 0 || y >= 32) {
            return false;
        } else {
            Chunk chunk = getChunkFromChunkCoords(x >> 2, z >> 2);
            return chunk.setBlockMetadata(x & 3, y, z & 3, metadata);
        }
    }

    public boolean isBlockNormal(int par1, int par2, int par3) {
        Block block = Block.blocksList[getBlockID(par1, par2, par3)];

        if (block == null) {
            return false;
        } else {
            return block.isNormal();
        }
    }

    public void notifyBlocks(int x, int y, int z, int blockId) {
        notifyBlocksOfNeighborChange(x, y, z, blockId);
    }

    public void notifyBlocksOfNeighborChange(int x, int y, int z, int blockID) {
        notifyBlockOfNeighborChange(x - 1, y, z, blockID);
        notifyBlockOfNeighborChange(x + 1, y, z, blockID);
        notifyBlockOfNeighborChange(x, y - 1, z, blockID);
        notifyBlockOfNeighborChange(x, y + 1, z, blockID);
        notifyBlockOfNeighborChange(x, y, z - 1, blockID);
        notifyBlockOfNeighborChange(x, y, z + 1, blockID);
    }

    private void notifyBlockOfNeighborChange(int x, int y, int z, int blockID) {
        Block block = Block.blocksList[getBlockID(x, y, z)];

        if (block != null) {
            block.onNeighborBlockChange(this, x, y, z, blockID);
        }
    }

    public RayObjectPosition rayTraceBlocks(Vec3D startVec, Vec3D lookVec, float distance) {
        Vec3D currentVec = new Vec3D(startVec);

        final int accuracy = 15;

        lookVec = new Vec3D(-lookVec.x / accuracy, lookVec.y / accuracy, -lookVec.z / accuracy);

        while (currentVec.distanceTo(startVec) <= distance) {
            currentVec = currentVec.addVector(lookVec);

            int inX = (int) currentVec.x;
            int inY = (int) currentVec.y;
            int inZ = (int) currentVec.z;

            int id = getBlockID(inX, inY, inZ);

            if (getChunkFromBlockCoords(inX, inZ).isEmptyChunk()) {
                return null;
            }

            if (id != 0 && Block.blocksList[id].getCollisionBoundingBoxFromPool(this, inX, inY, inZ).isVecInside(currentVec)) {
                return getMovingObjectPosition(currentVec, lookVec);
            }
        }

        return null;
    }

    private RayObjectPosition getMovingObjectPosition(Vec3D currentVec, Vec3D lookVec) {
        Vec3D lastVec = lookVec.subtractVector(currentVec);

        int x = (int) currentVec.x;
        int y = (int) currentVec.y;
        int z = (int) currentVec.z;

        int xL = (int) lastVec.x;
        int yL = (int) lastVec.y;
        int zL = (int) lastVec.z;

        int side = -1;

        if (zL > z) {
            side = 0;
        } else if (zL < z) {
            side = 1;
        } else if (xL > x) {
            side = 2;
        } else if (xL < x) {
            side = 3;
        } else if (yL > y) {
            side = 4;
        } else if (yL < y) {
            side = 5;
        }

        return new RayObjectPosition(x, y, z, side, currentVec);
    }

    public boolean canBlockBePlacedAt(int id, int x, int y, int z, int side) {
        int i = getBlockID(x, y, z);
        Block block = Block.blocksList[i];
        Block block1 = Block.blocksList[id];

        if (block1 != null) {
            AxisAlignedBB axisalignedbb = block1.getCollisionBoundingBoxFromPool(this, x, y, z);

            if (cc.player.getBoundingBox().collidesWith(axisalignedbb)) {
                return false;
            }
        }

        return id > 0 && block1.canPlaceBlockOnSide(this, x, y, z, side);
    }
}
