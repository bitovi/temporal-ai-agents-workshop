package bitovi.workflow.types;

/**
 * Defines the type of context entry in the conversation flow.
 * Each type corresponds to a different element in the ReAct loop or conversation.
 */
public enum ContextEntryType {
    /** User message in the conversation */
    USER_MESSAGE,
    
    /** Agent's reasoning/thought process */
    THOUGHT,
    
    /** Agent's action to be executed */
    ACTION,
    
    /** Result of an action execution */
    OBSERVATION,
    
    /** Final answer from the agent */
    ANSWER,
    
    /** Summary of compacted context or long-term memory */
    SUMMARY
}
