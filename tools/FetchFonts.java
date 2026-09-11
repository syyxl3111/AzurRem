import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * 用 JVM 下载字体。
 *
 * 两个环境坑：
 *  1. 沙箱里 curl / PowerShell 的 schannel 不可用（SEC_E_NO_CREDENTIALS），
 *     但 Java 自带信任库，HTTPS 正常。
 *  2. 这台机器解析不了 raw.githubusercontent.com，所以走 api.github.com 的
 *     git/blobs 接口拿 base64 内容再本地解码。
 */
public class FetchFonts {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record Spec(String sha, String outName) {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);

        // google/fonts 的 blob SHA（Inter 与 JetBrains Mono 都只有可变字体）
        Spec[] specs = {
            new Spec("047c92f6e2212473dc436020afed689527076d44", "inter.ttf"),
            new Spec("aa310be8b717fe3774f9444dd89d5f4101cc6d10", "jetbrains_mono.ttf"),
        };

        for (Spec spec : specs) {
            Path out = dir.resolve(spec.outName());
            try {
                String url = "https://api.github.com/repos/google/fonts/git/blobs/" + spec.sha();
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(90))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/vnd.github+json")
                        .GET().build();

                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    System.out.println("FAIL " + spec.outName() + "  HTTP " + resp.statusCode());
                    continue;
                }

                String body = resp.body();
                int marker = body.indexOf("\"content\"");
                if (marker < 0) {
                    System.out.println("FAIL " + spec.outName() + "  no content field");
                    continue;
                }
                int start = body.indexOf('"', body.indexOf(':', marker)) + 1;
                int end = body.indexOf('"', start);
                String b64 = body.substring(start, end).replace("\\n", "").replace("\n", "");

                byte[] data = Base64.getDecoder().decode(b64);
                Files.write(out, data);
                System.out.println("OK   " + spec.outName() + "  " + data.length + " bytes");
            } catch (Exception e) {
                System.out.println("FAIL " + spec.outName() + "  "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
