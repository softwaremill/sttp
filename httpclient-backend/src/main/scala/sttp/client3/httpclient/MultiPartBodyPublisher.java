package sttp.client3.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// https://stackoverflow.com/questions/46392160/java-9-httpclient-send-a-multipart-form-data-request/54675316#54675316
public class MultiPartBodyPublisher {
    private List<PartsSpecification> partsSpecificationList = new ArrayList<>();
    private String boundary = UUID.randomUUID().toString();

    public HttpRequest.BodyPublisher build() {
        if (partsSpecificationList.size() == 0) {
            throw new IllegalStateException("Must have at least one part to build multipart message.");
        }
        addFinalBoundaryPart();
        return HttpRequest.BodyPublishers.ofByteArrays(PartsIterator::new);
    }

    public String getBoundary() {
        return boundary;
    }

    public MultiPartBodyPublisher addPart(String name, String value, Map<String, String> headers) {
        PartsSpecification newPart = new PartsSpecification();
        newPart.type = PartsSpecification.TYPE.STRING;
        newPart.name = name;
        newPart.value = value;
        newPart.headers = headers;
        partsSpecificationList.add(newPart);
        return this;
    }

    public MultiPartBodyPublisher addPart(String name, Path value, Map<String, String> headers) {
        PartsSpecification newPart = new PartsSpecification();
        newPart.type = PartsSpecification.TYPE.FILE;
        newPart.name = name;
        newPart.path = value;
        newPart.headers = headers;
        partsSpecificationList.add(newPart);
        return this;
    }

    public MultiPartBodyPublisher addPart(String name, Supplier<InputStream> value, Map<String, String> headers) {
        PartsSpecification newPart = new PartsSpecification();
        newPart.type = PartsSpecification.TYPE.STREAM;
        newPart.name = name;
        newPart.stream = value;
        newPart.headers = headers;
        partsSpecificationList.add(newPart);
        return this;
    }

    private void addFinalBoundaryPart() {
        PartsSpecification newPart = new PartsSpecification();
        newPart.type = PartsSpecification.TYPE.FINAL_BOUNDARY;
        newPart.value = "--" + boundary + "--";
        partsSpecificationList.add(newPart);
    }

    static class PartsSpecification {

        public enum TYPE {
            STRING, FILE, STREAM, FINAL_BOUNDARY
        }

        PartsSpecification.TYPE type;
        String name;
        String value;
        Path path;
        Supplier<InputStream> stream;
        Map<String, String> headers = new HashMap<>();
    }

    class PartsIterator implements Iterator<byte[]> {

        private Iterator<PartsSpecification> iter;
        private InputStream currentFileInput;

        private boolean done;
        private byte[] next;

        PartsIterator() {
            iter = partsSpecificationList.iterator();
        }

        @Override
        public boolean hasNext() {
            if (done) return false;
            if (next != null) return true;
            try {
                next = computeNext();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (next == null) {
                done = true;
                return false;
            }
            return true;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            byte[] res = next;
            next = null;
            return res;
        }

        private byte[] computeNext() throws IOException {
            if (currentFileInput == null) {
                if (!iter.hasNext()) return null;
                PartsSpecification nextPart = iter.next();
                String contentHeaders = nextPart.headers.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\r\n"));
                if (PartsSpecification.TYPE.STRING.equals(nextPart.type)) {
                    String part = "--" + boundary + "\r\n" +
                            contentHeaders + "\r\n\r\n" +
                            nextPart.value + "\r\n";
                    return part.getBytes(StandardCharsets.UTF_8);
                }
                if (PartsSpecification.TYPE.FINAL_BOUNDARY.equals(nextPart.type)) {
                    return nextPart.value.getBytes(StandardCharsets.UTF_8);
                }
                if (PartsSpecification.TYPE.FILE.equals(nextPart.type)) {
                    Path path = nextPart.path;
                    currentFileInput = Files.newInputStream(path);
                } else {
                    currentFileInput = nextPart.stream.get();
                }
                String partHeader =
                        "--" + boundary + "\r\n" +
                                contentHeaders + "\r\n\r\n";
                return partHeader.getBytes(StandardCharsets.UTF_8);
            } else {
                byte[] buf = new byte[8192];
                int r = currentFileInput.read(buf);
                if (r > 0) {
                    byte[] actualBytes = new byte[r];
                    System.arraycopy(buf, 0, actualBytes, 0, r);
                    return actualBytes;
                } else {
                    currentFileInput.close();
                    currentFileInput = null;
                    return "\r\n".getBytes(StandardCharsets.UTF_8);
                }
            }
        }
    }
}
