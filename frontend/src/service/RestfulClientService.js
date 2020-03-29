export function getData(path, callback, onError) {
    console.log("Fetching data from " + path);
    fetch(path, {}).then(res => {
            return res.json();
        })
        .then((data) => {
            if (callback){
                callback(data);
            }
        })
        .catch(error => {
            if (onError){
                onError(error);
            }
            console.error(error);
        })
}

export async function postData(path, message, callback, onError) {
    console.log("Posting data to " + path + " " + JSON.stringify(message));
    try {
        const options = {
            method: 'post',
            body: JSON.stringify(message),
            headers: {
                'Content-Type': 'application/json'
            },
        };
        const response = await fetch(path, options);
        if (callback) {
            if (response.ok === false){
                if (onError){
                    onError(response.statusText);
                }
            }else{
                try {
                    let data = await response.json();
                    callback(data);
                } catch (e) {
                    callback(null);
                }
            }

        }
    } catch (error) {
        if (onError) {
            onError(error);

    }
    console.error('Error:', error);
    }
}
