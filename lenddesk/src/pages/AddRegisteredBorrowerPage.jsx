import { Link, useNavigate } from "react-router-dom";
import BorrowerDirectoryPicker from "../components/BorrowerDirectoryPicker";
import { useDataSource } from "../hooks/useDataSource";

export default function AddRegisteredBorrowerPage() {
  const { organizationId, refresh } = useDataSource();
  const navigate = useNavigate();
  return (
    <section className="bd-full-page">
      <Link to="/borrowers">← Back to borrowers</Link>
      <BorrowerDirectoryPicker
        organizationId={organizationId}
        onConnected={() => {
          refresh();
          navigate("/borrowers");
        }}
      />
    </section>
  );
}
