import React from "react";
import { Route, Switch, HashRouter } from "react-router-dom";
import "./App.css";
import MyAccounts from "./components/MyAccounts";
import Account from "./components/Account";
import CreateAccount from "./components/CreateAccount";
import SystemMessage from "./components/SystemMessage";

function App() {
    return (
        <div className="App container">
            <nav className="row navbar navbar-light mb-2">
                <h2><a href="/accounts">UI Demo</a></h2>
            </nav>
            <SystemMessage/>
            <HashRouter>
                <div>
                    <Switch>
                        <Route path="/account/:id" component={Account} />
                        <Route
                            path="/createAccount"
                            component={CreateAccount}
                        />
                        <Route path="/accounts" component={MyAccounts} />
                        <Route path="/" component={MyAccounts} />
                    </Switch>
                </div>
            </HashRouter>
        </div>
    );
}

export default App;
