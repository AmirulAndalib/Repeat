package staticResources;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import core.languageHandler.Language;

public abstract class AbstractNativeLanguageBootstrapResource extends AbstractBootstrapResource {

	private static final Logger LOGGER = Logger.getLogger(AbstractNativeLanguageBootstrapResource.class.getName());

	@Override
	public final void extractResources() throws IOException, URISyntaxException {
		super.extractResources();
		if (!generateKeyCode()) {
			LOGGER.warning("Unable to generate key code");
		}
	}

	@Override
	protected final String getName() {
		return getLanguage().name();
	}

	protected abstract Language getLanguage();
	protected abstract boolean generateKeyCode();
	public abstract File getIPCClient();
}
