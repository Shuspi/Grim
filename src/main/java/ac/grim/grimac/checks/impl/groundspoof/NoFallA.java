package ac.grim.grimac.checks.impl.groundspoof;

import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import ac.grim.grimac.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.event.impl.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;

import java.util.ArrayList;
import java.util.List;

// Catches NoFalls that obey the (1 / 64) rule
@CheckData(name = "NoFall A")
public class NoFallA extends PacketCheck {

    public boolean playerUsingNoGround = false;

    public NoFallA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            // We have the wrong world cached with chunks
            if (player.bukkitPlayer.getWorld() != player.playerWorld) return;
            // The player hasn't spawned yet
            if (player.getSetbackTeleportUtil().insideUnloadedChunk()) return;

            PacketWrapper wrapper = null;
            boolean hasPosition = false;

            // Flying packet types
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
                wrapper = new WrapperPlayClientPlayerPosition(event);
                hasPosition = true;
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                wrapper = new WrapperPlayClientPlayerPositionAndRotation(event);
                hasPosition = true;
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
                wrapper = new WrapperPlayClientPlayerRotation(event);
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) {
                wrapper = new WrapperPlayClientPlayerFlying(event);
            }

            assert wrapper != null;

            // Force teleports to have onGround set to false, might patch NoFall on some version.
            if (player.packetStateData.lastPacketWasTeleport) {
                setOnGround(wrapper, false);
                return;
            }

            // The prediction based NoFall check wants us to make the player take fall damage - patches NoGround NoFall
            // NoGround works because if you never touch the ground, you never take fall damage
            // So we make the player touch the ground, and therefore they take fall damage
            if (playerUsingNoGround) {
                playerUsingNoGround = false;
                setOnGround(wrapper, true);
                return;
            }

            // If the player claims to be on the ground
            if (onGround(wrapper)) {
                if (!hasPosition) {
                    if (!is003OnGround(onGround(wrapper))) setOnGround(wrapper, false);
                    return;
                }

                SimpleCollisionBox feetBB;

                Vector3d position = new Vector3d(player.x, player.y, player.z);
                Vector3d lastPos = new Vector3d(player.lastX, player.lastY, player.lastZ);

                feetBB = GetBoundingBox.getBoundingBoxFromPosAndSize(position.getX(), position.getY(), position.getZ(), 0.6, 0.001);

                // Don't expand if the player moved more than 50 blocks this tick (stop netty crash exploit)
                if (position.distanceSquared(lastPos) < 2500)
                    feetBB.expandToAbsoluteCoordinates(lastPos.getX(), lastPos.getY(), lastPos.getZ());

                // Shulkers have weird BB's that the player might be standing on
                if (Collisions.hasMaterial(player, feetBB, blockData -> Materials.isShulker(blockData.getType())))
                    return;

                // This is to support stepping movement (Not blatant, we need to wait on prediction engine to flag this)
                // This check mainly serves to correct blatant onGround cheats
                feetBB.expandMin(0, -4, 0);

                if (checkForBoxes(feetBB)) return;

                setOnGround(wrapper, false);
            }
        }
    }

    private void setOnGround(PacketWrapper wrapper, boolean onGround) {
        if (wrapper instanceof WrapperPlayClientPlayerPosition) {
            ((WrapperPlayClientPlayerPosition) wrapper).setOnGround(onGround);
        } else if (wrapper instanceof WrapperPlayClientPlayerPositionAndRotation) {
            ((WrapperPlayClientPlayerPositionAndRotation) wrapper).setOnGround(onGround);
        } else if (wrapper instanceof WrapperPlayClientPlayerRotation) {
            ((WrapperPlayClientPlayerRotation) wrapper).setOnGround(onGround);
        } else if (wrapper instanceof WrapperPlayClientPlayerFlying) {
            ((WrapperPlayClientPlayerFlying) wrapper).setOnGround(onGround);
        }
    }

    private boolean onGround(PacketWrapper wrapper) {
        if (wrapper instanceof WrapperPlayClientPlayerPosition) {
            return ((WrapperPlayClientPlayerPosition) wrapper).isOnGround();
        } else if (wrapper instanceof WrapperPlayClientPlayerPositionAndRotation) {
            return ((WrapperPlayClientPlayerPositionAndRotation) wrapper).isOnGround();
        } else if (wrapper instanceof WrapperPlayClientPlayerRotation) {
            return ((WrapperPlayClientPlayerRotation) wrapper).isOnGround();
        } else if (wrapper instanceof WrapperPlayClientPlayerFlying) {
            return ((WrapperPlayClientPlayerFlying) wrapper).isOnGround();
        }
        return false;
    }

    public boolean is003OnGround(boolean onGround) {
        if (onGround) {
            SimpleCollisionBox feetBB = GetBoundingBox.getBoundingBoxFromPosAndSize(player.x, player.y, player.z, 0.6, 0.001);
            feetBB.expand(0.03); // 0.03 can be in any direction

            return checkForBoxes(feetBB);
        }
        return true;
    }

    private boolean checkForBoxes(SimpleCollisionBox playerBB) {
        List<SimpleCollisionBox> boxes = new ArrayList<>();
        Collisions.getCollisionBoxes(player, playerBB, boxes, false);

        for (SimpleCollisionBox box : boxes) {
            if (playerBB.collidesVertically(box)) { // If we collide vertically but aren't in the block
                return true;
            }
        }

        return player.compensatedWorld.isNearHardEntity(playerBB.copy().expand(4));
    }
}
