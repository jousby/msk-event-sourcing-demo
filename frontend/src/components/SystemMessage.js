 
import React, {useState, useEffect} from 'react';
import SystemMessageStore from "../store/SystemMessageStore";
 

function SystemMessage() {
    const [message, setMessage] = useState(SystemMessageStore.getMessage());

    useEffect(() => {
        SystemMessageStore.addChangeListener(getSystemMessage);
        return (() => {
            SystemMessageStore.removeChangeListener(getSystemMessage);
        });
    }, []);

    function getSystemMessage() {
        setMessage(SystemMessageStore.getMessage());
    }

    function clearMessage(e) {
        SystemMessageStore.clear();
   }

    return (
        <div className="sticky-top">
            {message.type != null &&
            <div className={message.type + " alert alert-dismissible fade show"} role="alert">
                <strong>{message.text}</strong>
                <button type="button" className="close" data-dismiss="alert" aria-label="Close" onClick={clearMessage}>
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            }
        </div>
    )
}

export default SystemMessage;