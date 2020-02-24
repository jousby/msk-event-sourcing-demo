import EventEmitter from 'events';

export const MessageType = {
    INFO: 'alert-primary',
    WARNING: 'alert-warning',
    SUCCESS: 'alert-success',
    ERROR: 'alert-danger'
}

export const SYSTEM_MESSAGE_CHANGE_EVENT='SYSTEM_MESSAGE_CHANGE_EVENT';

class SystemMessageStore extends EventEmitter {

    constructor (){
        super();
        this.message = {};
    }

    getMessage(){
        return this.message;
    }

    setMessage(type, text){
        this.message = {
            type: type,
            text: text
        };
        this.emit(SYSTEM_MESSAGE_CHANGE_EVENT);
    }

    clear() {
       this.setMessage(null,'');
    }

    addChangeListener(callback){
        this.on(SYSTEM_MESSAGE_CHANGE_EVENT, callback);
    }

    removeChangeListener(callback){
        this.removeListener(SYSTEM_MESSAGE_CHANGE_EVENT, callback);
    }

}

export default new SystemMessageStore();
