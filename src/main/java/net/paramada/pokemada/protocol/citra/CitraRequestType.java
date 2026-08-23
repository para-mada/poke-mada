package net.paramada.pokemada.protocol.citra;

public enum CitraRequestType {
    READ_MEMORY(1),
    WRITE_MEMORY(2);

    private final int code;

    CitraRequestType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CitraRequestType fromCode(int code) {
        for (CitraRequestType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Citra request type: " + Integer.toUnsignedString(code));
    }
}
