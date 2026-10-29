// Icons from the same set EMIS 2.0 uses.
import { ChartColumnIncreasing, ClipboardCheck, ClipboardPen, FileText, LayoutDashboard, ListChecks, ShieldCheck, Target } from 'lucide-react';

// Every page of the app, in menu order. A user sees a page only if they have its capability
// (the backend decides capabilities from their roles; see AccessService.java).
export const PAGES = [
  // Published dashboards: Minister, CPU analyst, DEOs and principals.
  { id: 'dashboards', capability: 'DASHBOARDS', group: 'Statistical & reporting', label: 'Dashboards', icon: LayoutDashboard,
    title: 'KPI dashboards',
    description: 'Dashboards on frozen returns, limited to your schools. They refresh every 15 minutes, or now with “Refresh now”.' },
  // The Superset builder: CPU analyst.
  { id: 'builder', capability: 'BUILD', group: 'Statistical & reporting', label: 'Chart builder', icon: ChartColumnIncreasing,
    title: 'Chart builder',
    description: 'Build charts and dashboards in Superset on frozen returns. Then publish dashboards to roles under Dashboard access.' },
  // Filling in forms: registrars.
  { id: 'forms', capability: 'FILL_FORMS', group: 'Data collection', label: 'My forms', icon: ClipboardPen,
    title: 'My forms',
    description: 'Fill in your school’s forms for this year and submit them. They go to the principal, the DEO and the CPU analyst.' },
  // Reviewing returns: principals, DEOs and the CPU analyst.
  { id: 'reviews', capability: 'REVIEW', group: 'Data collection', label: 'Reviews', icon: ClipboardCheck,
    title: 'Reviews',
    description: 'Registrar → Principal → DEO → CPU analyst → Frozen. Approve to move a return on, or send it one step back with a comment.' },
  // The KPI registry: CPU analyst.
  { id: 'kpis', capability: 'MANAGE_KPIS', group: 'KPI set-up', label: 'KPI registry', icon: Target,
    title: 'KPI registry',
    description: 'Define each KPI: its formula from library fields, and which schools report it.' },
  // The field library: CPU analyst.
  { id: 'fields', capability: 'MANAGE_KPIS', group: 'KPI set-up', label: 'Field library', icon: ListChecks,
    title: 'Field library',
    description: 'Everything a form can ask. One field can feed many KPIs and appear in many forms.' },
  // The form builder: CPU analyst.
  { id: 'formBuilder', capability: 'MANAGE_KPIS', group: 'KPI set-up', label: 'Form builder', icon: FileText,
    title: 'Form builder',
    description: 'Build forms with the Form.io builder and link them to KPIs. Published forms go to the registrars of schools the KPIs cover.' },
  // Dashboard access: CPU analyst.
  { id: 'access', capability: 'MANAGE_KPIS', group: 'KPI set-up', label: 'Dashboard access', icon: ShieldCheck,
    title: 'Dashboard access',
    description: 'Choose which roles may open each Superset dashboard.' },
];

// The pages a user may open.
export function pagesFor(viewer) {
  // Keep the pages whose capability the user has.
  return PAGES.filter((page) => viewer.capabilities.includes(page.capability));
}
