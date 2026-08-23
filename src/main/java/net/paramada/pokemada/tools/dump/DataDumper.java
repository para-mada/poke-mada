package net.paramada.pokemada.tools.dump;

import net.paramada.pokemada.platform.AppDirectories;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Standalone, read-only RAM dumper for data research and reproducible bug reports. */
public final class DataDumper {
    private static final DateTimeFormatter DIRECTORY_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);

    private DataDumper() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help()) {
            printUsage();
            return;
        }

        Path output = options.output() == null ? defaultOutputDirectory(options.global()) : options.output();

        System.out.printf("Conectando a %s:%d...%n", options.host(), options.port());
        try (CitraUdpClient memory = new CitraUdpClient(
                options.host(), options.port(), Duration.ofSeconds(options.timeoutSeconds()))) {
            if (options.global()) {
                List<MemoryDumpRegion> regions = new ArrayList<>(GlobalMemoryDumpProfile.regions());
                regions.addAll(options.additionalRegions());
                System.out.println("Imagen lógica dispersa: 1 GiB; RAM respaldada a capturar: "
                        + regions.stream().mapToLong(MemoryDumpRegion::length).sum() / (1024 * 1024) + " MiB");
                GlobalDataDumpService.GlobalDumpResult result = new GlobalDataDumpService().dump(
                        output, GlobalMemoryDumpProfile.LOGICAL_SIZE, regions, memory::readMemory);
                System.out.println();
                System.out.println("Dump global completado: " + result.image());
                System.out.println("Manifest: " + result.manifest());
                return;
            }

            List<MemoryDumpRegion> regions = new ArrayList<>(SmDataDumpProfile.regions());
            regions.addAll(options.additionalRegions());
            DataDumpService.DumpResult result = new DataDumpService()
                    .dump(output, SmDataDumpProfile.ID, regions, memory::readMemory);
            System.out.println();
            System.out.println("Payload RPC efectivo: " + memory.effectiveMaximumReadSize() + " bytes");
            System.out.println("Dump completado: " + result.outputDirectory());
            System.out.println("Manifest: " + result.manifest());
        }
    }

    private static Path defaultOutputDirectory(boolean global) {
        String identifier = DIRECTORY_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        String profile = global ? GlobalMemoryDumpProfile.ID : SmDataDumpProfile.ID;
        return AppDirectories.dataDirectory().resolve("dumps").resolve(profile).resolve(identifier);
    }

    private static void printUsage() {
        System.out.println("""
                Master V Data Dumper (solo lectura)

                Uso:
                  DataDumper [--global] [--host HOST] [--port PORT] [--timeout SEGUNDOS] [--output DIRECTORIO]
                             [--range NOMBRE:DIRECCION:LONGITUD ...]

                Sin argumentos captura las regiones conocidas de Pokemon Sun/Moon y escribe en:
                  %LOCALAPPDATA%\\PokeMada\\dumps\\pokemon-sm

                Ejemplo de rango adicional:
                  --range research-area:0x330d5934:0xde0

                --global crea una imagen virtual dispersa de 1 GiB y captura las regiones RAM
                reales de New 3DS. Si se interrumpe, repite --global con el mismo --output.
                """);
    }

    record Options(String host, int port, int timeoutSeconds, Path output,
                   List<MemoryDumpRegion> additionalRegions, boolean global, boolean help) {
        static Options parse(String[] args) {
            String host = "localhost";
            int port = CitraUdpClient.DEFAULT_PORT;
            int timeout = 5;
            Path output = null;
            boolean help = false;
            boolean global = false;
            List<MemoryDumpRegion> ranges = new ArrayList<>();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                switch (argument) {
                    case "--host" -> host = value(args, ++index, argument);
                    case "--port" -> port = positiveInt(value(args, ++index, argument), "port");
                    case "--timeout" -> timeout = positiveInt(value(args, ++index, argument), "timeout");
                    case "--output" -> output = Path.of(value(args, ++index, argument));
                    case "--range" -> ranges.add(parseRange(value(args, ++index, argument)));
                    case "--global" -> global = true;
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException("Argumento desconocido: " + argument);
                }
            }
            if (port > 65_535) throw new IllegalArgumentException("port must be between 1 and 65535");
            return new Options(host, port, timeout, output, List.copyOf(ranges), global, help);
        }

        private static MemoryDumpRegion parseRange(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("range must use NAME:ADDRESS:LENGTH");
            }
            long address = number(parts[1], "range address");
            long length = number(parts[2], "range length");
            if (length > Integer.MAX_VALUE) throw new IllegalArgumentException("range length is too large");
            return new MemoryDumpRegion(parts[0], address, (int) length);
        }

        private static long number(String value, String name) {
            try {
                return value.startsWith("0x") || value.startsWith("0X")
                        ? Long.parseUnsignedLong(value.substring(2), 16)
                        : Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " is not a valid number: " + value, exception);
            }
        }

        private static int positiveInt(String value, String name) {
            long number = number(value, name);
            if (number < 1 || number > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " must be a positive integer");
            }
            return (int) number;
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
            return args[index];
        }
    }
}
