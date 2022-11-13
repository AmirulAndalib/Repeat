package core.userDefinedTask;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import argo.jdom.JsonNode;
import argo.jdom.JsonNodeFactories;
import argo.jdom.JsonRootNode;
import core.controller.Core;
import core.keyChain.KeyChain;
import core.keyChain.MouseGesture;
import core.keyChain.TaskActivation;
import core.languageHandler.Language;
import core.languageHandler.compiler.AbstractNativeCompiler;
import core.languageHandler.compiler.DynamicCompilerManager;
import core.languageHandler.compiler.RemoteRepeatsCompiler;
import core.userDefinedTask.internals.TaskSourceHistory;
import core.userDefinedTask.internals.TaskSourceHistoryEntry;
import core.userDefinedTask.internals.preconditions.TaskExecutionPreconditions;
import utilities.FileUtility;
import utilities.ILoggable;
import utilities.json.IJsonable;

public abstract class UserDefinedAction implements IJsonable, ILoggable {

	private static final Logger LOGGER = Logger.getLogger(UserDefinedAction.class.getName());

	protected String actionId;
	protected String name;

	private TaskExecutionPreconditions executionPreconditions;
	private TaskActivation activation;

	protected String sourcePath;
	protected Language compiler;
	protected boolean enabled;

	protected TaskActivation invoker;
	protected KeyChain invokingKeyChain;
	protected MouseGesture invokingMouseGesture;

	// This is to enable invoking task programmatically.
	protected TaskInvoker taskInvoker;

	protected UsageStatistics statistics;
	protected TaskSourceHistory sourceHistory;

	public UserDefinedAction() {
		this(UUID.randomUUID().toString());
	}

	protected UserDefinedAction(String actionId) {
		this.actionId = actionId;
		executionPreconditions = TaskExecutionPreconditions.defaultConditions();
		activation = TaskActivation.newBuilder().build();
		invoker = TaskActivation.newBuilder().build();
		invokingKeyChain = new KeyChain();
		statistics = new UsageStatistics();
		sourceHistory = new TaskSourceHistory();
		enabled = true;
	}

	/**
	 * Custom action defined by user
	 * @param controller See {@link core.controller.Core} class.
	 * @throws InterruptedException
	 */
	public abstract void action(Core controller) throws InterruptedException;

	/**
	 * Perform the action and track the statistics related to this action.
	 * @param controller See {@link core.controller.Core} class.
	 * @throws InterruptedException
	 */
	public final void trackedAction(Core controller) throws InterruptedException {
		String executionId = statistics.useNow(ExecutionContext.Builder.of().setActivation(invoker).setController(controller).build());
		action(controller);
		statistics.executionFinished(executionId);
	}

	/**
	 * @return this action's ID.
	 */
	public final String getActionId() {
		return actionId;
	}

	/**
	 * @return the default namespace for this action.
	 */
	public String getNamespace() {
		return SharedVariables.GLOBAL_NAMESPACE;
	}

	/**
	 * Set the name of the action.
	 *
	 * @param name new name of the action.
	 */
	public final void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the execution preconditions for this task.
	 */
	public final TaskExecutionPreconditions getExecutionPreconditions() {
		return executionPreconditions;
	}

	/**
	 * @return the activation entity associated with this action.
	 */
	public final TaskActivation getActivation() {
		return activation;
	}

	public final String getName() {
		return name;
	}

	public final String getSourcePath() {
		return sourcePath;
	}

	public String getSource() {
		StringBuffer source = FileUtility.readFromFile(sourcePath);
		if (source == null) {
			return null;
		}

		return source.toString();
	}

	public final void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public final void recordSourceHistory(String sourcePath) {
		sourceHistory.addEntry(TaskSourceHistoryEntry.of(sourcePath));
	}

	public final Language getCompiler() {
		return compiler;
	}

	public final void setCompiler(Language compiler) {
		this.compiler = compiler;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public final void setExecutionPreconditions(TaskExecutionPreconditions preconditions) {
		this.executionPreconditions = preconditions;
	}

	public final void setActivation(TaskActivation activation) {
		this.activation = activation;
	}

	public void setTaskInvoker(TaskInvoker taskInvoker) {
		this.taskInvoker = taskInvoker;
	}

	public final UsageStatistics getStatistics() {
		return statistics;
	}

	public final TaskSourceHistory getTaskSourceHistory() {
		return sourceHistory;
	}

	public final void override(UserDefinedAction other) {
		setName(other.getName());
		activation.copy(other.activation);
		statistics = other.statistics;
		sourceHistory.addHistory(other.sourceHistory);
	}

	/**
	 * This method is called to dynamically allow the current task to determine which activation triggered it.
	 * This activation would contain only the activation element that triggered the event.
	 */
	public void setInvoker(TaskActivation invoker) {
		this.invoker = invoker;

		// For legacy purpose.
		setInvokingKeyChain(invoker.getFirstHotkey());
		setInvokingMouseGesture(invoker.getFirstMouseGesture());
	}

	/**
	 * This method is called to dynamically allow the current task to determine which key chain activated it among
	 * its hotkeys. This will only change the key chain definition of the current key chain, not substituting the real object.
	 *
	 * This will also set {@link #invokingMouseGesture} to null.
	 *
	 * @param invokingKeyChain
	 * @deprecated use {@link #setInvoker(TaskActivation)} instead.
	 */
	@Deprecated
	private final void setInvokingKeyChain(KeyChain invokingKeyChain) {
		if (invokingKeyChain == null) {
			return;
		}

		this.invokingKeyChain.clearKeys();
		this.invokingKeyChain.addFrom(invokingKeyChain);
	}

	/**
	 * This method is called to dynamically allow the current task to determine which key chain activated it among
	 * its mouse gestures.
	 *
	 * This will also clear {@link #invokingKeyChain}.
	 *
	 * @param invokingMouseGesture
	 * @deprecated use {@link #setInvoker(TaskActivation)} instead.
	 */
	@Deprecated
	private final void setInvokingMouseGesture(MouseGesture invokingMouseGesture) {
		if (invokingMouseGesture == null) {
			return;
		}

		this.invokingMouseGesture = invokingMouseGesture;
	}


	/***********************************************************************/
	public UserDefinedAction recompileNative(AbstractNativeCompiler compiler) {
		return this;
	}

	public UserDefinedAction recompileRemote(RemoteRepeatsCompiler compiler) {
		return this;
	}

	public final void syncContent(UserDefinedAction other) {
		sourcePath = other.sourcePath;
		compiler = other.compiler;
		name = other.name;
		activation.copy(other.activation);
		enabled = other.enabled;
	}

	/***********************************************************************/
	@Override
	public JsonRootNode jsonize() {
		return JsonNodeFactories.object(
				JsonNodeFactories.field("action_id", JsonNodeFactories.string(actionId)),
				JsonNodeFactories.field("source_path", JsonNodeFactories.string(sourcePath)),
				JsonNodeFactories.field("compiler", JsonNodeFactories.string(compiler.toString())),
				JsonNodeFactories.field("name", JsonNodeFactories.string(name)),
				JsonNodeFactories.field("execution_preconditions", executionPreconditions.jsonize()),
				JsonNodeFactories.field("activation", activation.jsonize()),
				JsonNodeFactories.field("enabled", JsonNodeFactories.booleanNode(enabled)),
				JsonNodeFactories.field("statistics", statistics.jsonize()),
				JsonNodeFactories.field("source_history", sourceHistory.jsonize())
				);
	}

	public static UserDefinedAction parseJSON(DynamicCompilerManager factory, JsonNode node) {
		if (node.isNode("composite_action")) {
			return CompositeUserDefinedAction.parseJSON(factory, node);
		}

		return parsePureJSON(factory, node);
	}

	protected static UserDefinedAction parsePureJSON(DynamicCompilerManager factory, JsonNode node) {
		try {
			String actionId = node.getStringValue("action_id");

			String sourcePath = node.getStringValue("source_path");
			AbstractNativeCompiler compiler = factory.getNativeCompiler(node.getStringValue("compiler"));
			if (compiler == null) {
				JOptionPane.showMessageDialog(null, "Unknown compiler " + node.getStringValue("compiler"));
				return null;
			}

			String name = node.getStringValue("name");
			TaskExecutionPreconditions executionPreconditions = TaskExecutionPreconditions.defaultConditions();
			if (node.isNode("execution_preconditions")) {
				executionPreconditions = TaskExecutionPreconditions.parseJSON(node.getNode("execution_preconditions"));
				if (executionPreconditions == null) {
					LOGGER.warning("Unable to parse execution preconditions.");
					executionPreconditions = TaskExecutionPreconditions.defaultConditions();
				}
			}

			JsonNode activationJSONs =  node.getNode("activation");
			TaskActivation activation = TaskActivation.parseJSON(activationJSONs);

			File sourceFile = new File(sourcePath);
			StringBuffer sourceBuffer = FileUtility.readFromFile(sourceFile);
			String source = null;
			if (sourceBuffer == null) {
				JOptionPane.showMessageDialog(null, "Cannot get source at path " + sourcePath);
				return null;
			} else {
				source = sourceBuffer.toString();
			}

			File objectFile = new File(FileUtility.joinPath("core", FileUtility.removeExtension(sourceFile).getName()));
			objectFile = FileUtility.addExtension(objectFile, compiler.getObjectExtension());
			UserDefinedAction output = compiler.compile(source, objectFile).action();
			if (output == null) {
				JOptionPane.showMessageDialog(null, "Compilation failed for task " + name + " with source at path " + sourcePath);
				return null;
			}

			UsageStatistics statistics = UsageStatistics.parseJSON(node.getNode("statistics"));
			if (statistics != null) {
				output.statistics = statistics;
			} else {
				output.statistics.createNow();
				LOGGER.warning("Unable to retrieve statistics for task " + name);
			}

			TaskSourceHistory sourceHistory = TaskSourceHistory.parseJSON(node.getNode("source_history"));
			if (sourceHistory == null) {
				LOGGER.warning("Unable to retrieve task source history for task " + name);
			} else {
				output.sourceHistory = sourceHistory;
			}

			boolean enabled = node.getBooleanValue("enabled");

			output.actionId = actionId;
			output.sourcePath = sourcePath;
			output.compiler = compiler.getName();
			output.name = name;
			output.executionPreconditions = executionPreconditions;
			output.activation = activation;
			output.enabled = enabled;

			return output;
		} catch (Exception e) {
			Logger.getLogger(UserDefinedAction.class.getName()).log(Level.WARNING, "Exception parsing task from JSON", e);
			return null;
		}
	}

	@Override
	public final Logger getLogger() {
		return Logger.getLogger(UserDefinedAction.class.getName());
	}
}