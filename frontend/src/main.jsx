// React's helper that draws our app into the page.
import { createRoot } from 'react-dom/client';
// Our main screen.
import CensusAnalyticsDashboard from './CensusAnalyticsDashboard';
// Bootstrap's styles, which the Form.io form builder and forms are drawn with.
import 'bootstrap/dist/css/bootstrap.min.css';
// Form.io's own styles (builder sidebar, drag and drop, dialogs) and its icon font.
import '@formio/js/dist/formio.full.min.css';
// The page's styles, loaded last so the EMIS look wins over Bootstrap's.
import './styles.css';

// Find the <div id="root"> in index.html.
const rootElement = document.getElementById('root');
// Now we will draw the app inside that div.
createRoot(rootElement).render(<CensusAnalyticsDashboard />);
