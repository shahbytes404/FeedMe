import {Component, type ReactNode, Suspense} from "react";
import {ErrorState} from "./components/ErrorState.tsx";
import {Navigate, Route, Routes, useSearchParams} from "react-router-dom";
import {LoadingScreen} from "./components/LoadingScreen.tsx";
import {AppSell} from "./components/AppShell.tsx";

class AppErrorBoundary extends Component<{ children: ReactNode }, { error: unknown }> {
    override state = {error: null as unknown};

    static getDerivedStateFromError(error: unknown) {
        return {error};
    }

    override render() {
        if (this.state.error) {
            return <ErrorState error={this.state.error}/>
        }

        return this.props.children;
    }
}

function DashboardRoute() {
    const [searchParams] = useSearchParams();
    const activeUserId = searchParams.get('user') ?? 'u1';

    return <Suspense fallback={<LoadingScreen/>}>
        <AppSell activeUserId={activeUserId}/>
    </Suspense>
}

function App() {
    return (
        <AppErrorBoundary>
            <Routes>
                <Route path="/" element={<DashboardRoute/>}/>
                <Route path="*" element={<Navigate to="/" replace/>}/>
            </Routes>
        </AppErrorBoundary>
    )
}

export default App
