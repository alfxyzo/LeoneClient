package dev.alfxyz.leoneclient.web;

import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * HTTP GETs for the website features. Certificates are checked against Java's
 * own trust store and, on Windows, the system store as well, so HTTPS keeps
 * working when antivirus software inspects traffic with its own root.
 */
public final class Http {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static volatile HttpClient client;

	private Http() {
	}

	/** A reply: its status, body, and for a 429 how many seconds to wait (0 when not given). */
	public record Response(int status, byte[] body, long retryAfter) {
		public String text() {
			return new String(body, java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	private static String userAgent() {
		String version = FabricLoader.getInstance().getModContainer("leoneclient")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		return "LeoneClient/" + version + " (+https://github.com/alfxyzo/LeoneClient)";
	}

	private static HttpClient client() {
		HttpClient c = client;
		if (c != null) return c;
		synchronized (Http.class) {
			if (client == null) {
				HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL);
				try {
					SSLContext ssl = SSLContext.getInstance("TLS");
					ssl.init(null, new TrustManager[] {trustManager()}, null);
					b.sslContext(ssl);
				} catch (Exception e) {
					LOGGER.warn("Leone Client: using the default trust store only", e);
				}
				client = b.build();
			}
			return client;
		}
	}

	private static X509TrustManager trustManager() throws Exception {
		List<X509TrustManager> managers = new ArrayList<>();
		managers.add(managerFor(null));
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
			try {
				KeyStore ks = KeyStore.getInstance("Windows-ROOT");
				ks.load(null, null);
				managers.add(managerFor(ks));
			} catch (Exception e) {
				LOGGER.debug("Leone Client: Windows trust store unavailable", e);
			}
		}
		return new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				managers.getFirst().checkClientTrusted(chain, authType);
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				CertificateException last = null;
				for (X509TrustManager m : managers) {
					try {
						m.checkServerTrusted(chain, authType);
						return;
					} catch (CertificateException e) {
						last = e;
					}
				}
				throw last;
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				List<X509Certificate> all = new ArrayList<>();
				for (X509TrustManager m : managers) all.addAll(List.of(m.getAcceptedIssuers()));
				return all.toArray(new X509Certificate[0]);
			}
		};
	}

	private static X509TrustManager managerFor(KeyStore ks) throws Exception {
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);
		for (TrustManager tm : tmf.getTrustManagers()) if (tm instanceof X509TrustManager x) return x;
		throw new IllegalStateException("no X509 trust manager");
	}

	public static CompletableFuture<Response> get(String url, String accept) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(12))
			.header("User-Agent", userAgent())
			.header("Accept", accept)
			.GET()
			.build();
		return client().sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
			long wait = 0;
			try {
				wait = Long.parseLong(r.headers().firstValue("Retry-After").orElse("0").strip());
			} catch (NumberFormatException ignored) {
			}
			return new Response(r.statusCode(), r.body(), wait);
		});
	}
}
