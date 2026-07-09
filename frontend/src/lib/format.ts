export function formatRelativeTime(value: string): string {
    const time = new Date(value).getTime();

    const diffInMinutes = Math.round((time - Date.now()) / 60000);

    const formatter = new Intl.RelativeTimeFormat('en', {numeric: 'auto'});

    if (Math.abs(diffInMinutes) < 60) {
        return formatter.format(diffInMinutes, 'minute');
    }

    const diffInHours = Math.round(diffInMinutes / 60);

    if (Math.abs(diffInHours) < 24) {
        return formatter.format(diffInHours, 'hour');
    }

    const diffInDays = Math.round(diffInHours / 24);

    return formatter.format(diffInDays, 'day');
}