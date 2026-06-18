import styles from './Input.module.css';

interface Props extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export default function Input({ label, error, className = '', ...rest }: Props) {
  return (
    <div className={styles.field}>
      {label && <label className={styles.label}>{label}</label>}
      <input
        className={`${styles.input} ${className}`}
        {...rest}
      />
      {error && <span className={styles.error}>{error}</span>}
    </div>
  );
}
