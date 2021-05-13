package ac.grim.grimac.utils.data;

import org.bukkit.util.Vector;

public class VectorData {
    public VectorType vectorType;
    public VectorData lastVector;
    public Vector vector;

    public VectorData(Vector vector, VectorData lastVector) {
        this.vector = vector;
        this.lastVector = lastVector;
        this.vectorType = lastVector.vectorType;
    }

    public VectorData(double x, double y, double z, VectorType vectorType) {
        this.vector = new Vector(x, y, z);
        this.vectorType = vectorType;
    }

    // For handling replacing the type of vector it is while keeping data
    // Not currently used as this system isn't complete
    public VectorData(Vector vector, VectorData lastVector, VectorType vectorType) {
        this.vector = vector;
        this.lastVector = lastVector;
        this.vectorType = vectorType;
    }

    public VectorData(Vector vector, VectorType vectorType) {
        this.vector = vector;
        this.vectorType = vectorType;
    }

    // TODO: For debugging everything should have it's own type!
    // Would make false positives really easy to fix
    // But seriously, we could trace the code to find the mistake
    public enum VectorType {
        Normal,
        Swimhop,
        Ladder,
        Knockback,
        PossibleKB,
        Hackyladder,
        Teleport,
        SkippedTicks
    }
}